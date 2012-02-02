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


/**
 * abstract node with several inputs and outputs
 */
public abstract class Function {
    int gateCount=0;
    int readyGateCount=0;

    private class Gate<T> {
        {gateCount++;}
        public T operand;
    }

    public class Input<T> extends Gate<T> implements OutPort<T> {
        protected boolean opndready = false;

        @Override
        public void send(T value) {
            if (opndready) {
                throw new IllegalStateException("illegal send");
            }
            synchronized (this) {
                operand = value;
                opndready = true;
                readyGateCount++;
                if (readyGateCount<gateCount) {
                    return;
                }
            }
            fire();
            
        }
    }
    
    public class Output<T> extends Gate<OutPort<T>> implements InPort<T> {
        protected int opndCount = 0;

        @Override
        public void connect(OutPort<T> sink) {
            synchronized (this) {
            	switch (opndCount++) {
            	case 0:
                    operand = sink;
                    readyGateCount++;
                    if (readyGateCount<gateCount) {
                        return;
                    }
                    break;
            	case 1:
                	DataSource<T> ds=new DataSource<T>();
                	ds.connect(operand);
                	ds.connect(sink);
                	operand=ds;
                    return;
                default:
                	((DataSource<T>)operand).connect(sink);
                    return;
            	}
            }
            fire();
        }
        
        public void send(T value) {
            operand.send(value);
        }
    }

    protected abstract void fire();
    
}