/*
 * Copyright 2011 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.rfqu.df4j.core;

import java.util.concurrent.Executor;

/**
 * General dataflow node with several inputs and outputs.
 * Firing occur when all inputs are filled.
 * Typical use case is:
 *  - create 1 or more pins for inputs and/or outputs
 *  - redefine abstract method act()
 */
public abstract class AbstractActor extends Task {
	static final int allOnes=0xFFFFFFFF;
    private Pin head; // the head of the list of Pins
    private int pinCount=0;
    private int pinMask=0; // mask with 1 for all existing pins
    private int readyPins=0;  // mask with 1 for all ready pins
    protected boolean fired=false; // true when this actor runs
    
    public AbstractActor(Executor executor) {
    	super(executor);
    }

    public AbstractActor() {
    }

    /**
     * @return true if the actor has all its pins on and so is ready for execution
     */
    protected final boolean isReady() {
		return readyPins==pinMask;
	}
    
    /** 
     * Extracts tokens from pins.
     * Extracted tokens are expected to be used used in the act() method.
     */
    protected void consumeTokens() {
        for (Pin pin=head; pin!=null; pin=pin.getNext()) {
            pin.consume();
        }
    }
    
    /** loops while all pins are ready
     */
    @Override
    public void run() {
        for (;;) {
            synchronized (this) {
                if (!isReady()) {
                    fired=false; // allow firing
                    return;
                }
                consumeTokens();
            }
            act();
        }
    }

    /**
     * reads extracted tokens from places and performs specific calculations 
     */
    protected abstract void act();

    /**
     * Basic place for input tokens.
     * Initial state should be empty, to prevent premature firing.
     */
    protected abstract class Pin {
        private Pin next; // link to list
    	private final int pinBit; // distinct for all other pins of the node 

    	protected Pin(){
        	synchronized (AbstractActor.this) {
            	int count = pinCount;
                if (count==32) {
              	  throw new IllegalStateException("only 32 pins could be created");
                }
                next=head; head=this; // register itself in the pin list
                pinBit = 1<<count;
            	pinMask=pinMask|pinBit;
                pinCount++;
			}
        	
        }

    	public Pin getNext() {
            return next;
        }

        /**
    	 * sets pin's bit on and fires task if all pins are on
    	 *  @return true if actor became ready and must be fired
    	 */
        protected boolean turnOn() {
            readyPins |= pinBit;
            if (fired || !isReady()) {
                return false;
            }
            fired = true; // to prevent multiple concurrent firings
            return true;
        }

        /**
         * sets pin's bit off
         */
        protected void turnOff() {
            readyPins &= ~pinBit;
        }

        protected boolean isOn() {
            return (readyPins&pinBit)!=0;
        }

        protected boolean isOff() {
            return (readyPins&pinBit)==0;
        }
        
        /**
         * Extracts token from the place and, if the token carries information,
         * saves that information..
         * Should return quickly, as is called from the actor's synchronized block.
         */
        protected abstract void consume();
    }

    /**
     *  Stops/allows actor execution. Functions as binary semaphore,
     */
    protected class Switch extends Pin {
    	
        public Switch() { }

        public void on() {
        	boolean doFire;
            synchronized (AbstractActor.this) {
            	if (isOn()) {
    				throw new IllegalStateException("turned on already"); 
            	}
            	doFire=turnOn();
            }
            if (doFire) {
            	fire();
            }
        }

        protected void consume() {
            if (isOff()) {
                throw new IllegalStateException("turned off already"); 
            }
            turnOff();
        }
    }

    /** 
     * holds tokens without data 
     */
    protected class Semaphore extends Pin {
        private int count=0;
        
        protected boolean isEmpty() {
            return count==0;
        }

        @Override
        protected void consume() {
            if (count==0) {
                throw new IllegalStateException("place is empty"); 
            }
            count--;
            if (count==0) {
                turnOff();
            }
        }

        public void up() {
            synchronized (AbstractActor.this) {
                count++;
                if (count==1) {
                    turnOn();
                }
            }
        }

        public void down() {
            synchronized (AbstractActor.this) {
                consume();
            }
        }
    }

    /**
     * Token storage with standard Port interface.
     * @param <T> type of accepted tokens.
     */
    protected abstract class BasePort<T> extends Pin implements Port<T> {
        /** extracted token */
        public T value=null;

        @Override
        public void send(T token) {
        	boolean doFire;
            synchronized (AbstractActor.this) {
            	add(token);
            	doFire=turnOn();
            }
            if (doFire) {
            	fire();
            }
        }

        protected void consume() {
            if (isEmpty() ) {
                throw new IllegalStateException("no tokens");
            }
            value = remove();
            if (isEmpty()) {
                turnOff();
            }
        }

        /**
         * saves passed token
         * @param newToken
         */
		protected abstract void add(T newToken);
		/**
		 * 
		 * @return true if the pin is not ready
		 */
		protected abstract boolean isEmpty();
		/**
		 * removes token from the storage
		 * @return removed token
		 */
		protected abstract T remove();
    }

    /** A place for single token of type <T>
     * @param <T> 
     */
    protected abstract class Input<T extends Link> extends BasePort<T> implements StreamPort<T>{
        protected boolean closeRequested=false;
        protected boolean closeHandled=false;

        /** Signals the end of the stream. 
         * Turns this pin on. Removed value is null 
         * (null cannot be send with StreamInput.add(message)).
         */
        @Override
        public void close() {
            boolean doFire;
            synchronized (AbstractActor.this) {
                closeRequested=true;
                doFire=turnOn();
            }
            if (doFire) {
                fire();
            }
        }
        
        public boolean isClosed() {
            return closeRequested;
        }
    }

    /** A place for single token of type <T>
     * @param <T> 
     */
    public class ScalarInput<T extends Link> extends Input<T> {
        protected boolean filled=false;

        @Override
        protected void add(T token) {
            if (token==null) {
                throw new IllegalArgumentException("operand may not be null"); 
            }
            value=token;
        }

        @Override
        public boolean isEmpty() {
            if (filled) {
                return false;
            }
            return closeRequested==closeHandled;
        }

        @Override
        protected T remove() {
            filled=false;
            if (value!=null) {
                return value;
            }
            if (closeRequested) {
                closeHandled=true;
            }
            return null;
        }
    }

    /** A Queue of tokens of type <T>
     * @param <T> 
     */
    public class StreamInput<T extends Link> extends Input<T> {
        private LinkedQueue<T> queue=new LinkedQueue<T>();

        @Override
        protected void add(T token) {
            if (token==null) {
                throw new IllegalArgumentException("operand may not be null"); 
            }
            queue.add(token);
        }

        @Override
        public boolean isEmpty() {
            if (!queue.isEmpty()) {
                return false;
            }
            return closeRequested==closeHandled;
        }

        @Override
        protected T remove() {
            T res = queue.poll();
            if (res!=null) {
                return res;
            }
            if (closeRequested) {
                closeHandled=true;
            }
            return null;
        }

    }

    /**
     * 
     * This pin carries demand(s) of the result.
     * Demand is two-fold: it is a pin, so firing possible only if
     * someone demanded the execution, and it holds consumer's port where
     * the result should be sent. 
     * @param <R>  type of result
     */
    public class Demand<R> extends Pin implements ResultSource<R>, Callback<R> {
        private Promise<R> listeners=new Promise<R>();

        /** indicates a demand
         * @param sink Port to send the result
         * @return 
         */
        @Override
        public ResultSource<R> addListener(Callback<R> sink) {
        	boolean doFire;
            synchronized (AbstractActor.this) {
            	listeners.addListener(sink);
            	doFire=turnOn();
            }
            if (doFire) {
            	fire();
            }
            return this;
    	}

    	/** satisfy demand(s)
    	 */
    	@Override
		public void send(R m) {
			listeners.send(m);
		}

        @Override
        public void sendFailure(Throwable exc) {
            listeners.sendFailure(exc);
        }

        @Override
        protected void consume() {}
    }
}