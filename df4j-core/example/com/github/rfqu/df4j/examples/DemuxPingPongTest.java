/*
 * Copyright 2011 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.rfqu.df4j.examples;

import java.io.PrintStream;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.github.rfqu.df4j.core.Actor;
import com.github.rfqu.df4j.core.DFContext;
import com.github.rfqu.df4j.core.Port;
import com.github.rfqu.df4j.core.Request;
import com.github.rfqu.df4j.ext.ActorLQ;
import com.github.rfqu.df4j.ext.Dispatcher;
import com.github.rfqu.df4j.ext.ImmediateExecutor;
import com.github.rfqu.df4j.testutil.MessageSink;

public class DemuxPingPongTest {
    
    final static int NUM_ACTORS = 1000; // number of Ping nodes
    final static int NUM_TOKENS = NUM_ACTORS; // number of tokens
    final static int TIME_TO_LIVE = 1000; // hops
    final static int times = 5;
    int nThreads;
    PrintStream out = System.out;

    @Before
    public void init() {
        out.println("Network with " + NUM_ACTORS + " nodes, " + NUM_TOKENS + " tokens, with " + TIME_TO_LIVE + " each, on " + nThreads + " threads");
    }

    @Test
    public void testImm() throws InterruptedException  {
        nThreads=1;
        final ImmediateExecutor immediateExecutor = new ImmediateExecutor();
        DFContext c=DFContext.getCurrentContext();
        c.setCurrentExecutor(immediateExecutor);
		runTest();
    }

    @Test
    public void testSingle() throws InterruptedException {
        nThreads=1;
        DFContext.setSingleThreadExecutor();
        runTest();
    }

    @Test
    public void testFixed() throws InterruptedException {
        nThreads= Runtime.getRuntime().availableProcessors();
        DFContext.setFixedThreadPool(nThreads);
        runTest();
    }

    private void runTest() throws InterruptedException {
        String workerName = DFContext.getCurrentExecutor().getClass().getCanonicalName();
        out.println("Using " + workerName);
		for (int i = 0; i < times; i++) {
            runPingPong();
        }
	}


    /**
     * the core of the test
     */
    float runPingPong() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        MessageSink<Token> sink = new MessageSink<Token>(NUM_TOKENS);
        Ping[] pings = new Ping[NUM_ACTORS];
        Random rand = new Random(1);

        // create Pong actor
        Pong pong = new Pong();
        // create Ping actors
        for (int i = 0; i < pings.length; i++) {
            pings[i] = new Ping(pong);
        }
        // create tokens, send them to randomly chosen Ping actors
        for (int i = 0; i < NUM_TOKENS; i++) {
            pings[rand.nextInt(pings.length)].post(new Token(TIME_TO_LIVE, sink));
        }

        // wait for all packets to die.
        sink.await();

        // report timings
        long etime = (System.currentTimeMillis() - startTime);
        float switchnum = NUM_TOKENS * ((long) TIME_TO_LIVE);
        float delay = etime * 1000 * nThreads / switchnum;
        out.println("Elapsed=" + etime / 1000f + " sec; rate=" + (1 / delay) + " messages/mks/core; mean hop time=" + (delay * 1000) + " ns");
        return delay;
    }

    /**
     * the type of messages floating between nodes
     */
    static class Token extends Request<Token, Void> {
        private int hops_remained;
        private final Port<Token> sink;

        public Token(int hops_remained, Port<Token> sink) {
            this.hops_remained = hops_remained;
            this.sink = sink;
        }
        
        /**
         * the method to handle incoming messages for each received packet,
         * decrease the number of remaining hops. If number of hops become zero,
         * send it to sink, otherwise send it back to the Ping actor.
         */
        protected void sendTo(Port<Token> to) throws Exception {
            int nextVal = hops_remained - 1;
            if (nextVal == 0) {
                sink.post(this);
            } else {
                hops_remained = nextVal;
                to.post(this);
            }
        }
    }
    
    /**
     * The pinging actor
     * 
     */
    static class Ping extends Actor<Token> {
        Port<Token> pong;

        public Ping(Port<Token> pong) {
            this.pong = pong;
        }

        /**
         * Handle incoming messages. For each received packet, decrease the
         * number of remaining hops. If number of hops become zero, send it to
         * sink, otherwise send to the Pong actor.
         */
        protected void act(Token token) throws Exception {
            token.setListener(this);
            token.sendTo(pong);
        }
    }

    static class Pong extends Dispatcher<Token> {
        { 
            for (int k=0; k<3; k++) {
                new PongWorker();
            }
        }
        
        /**
         * The ponging actor
         * 
         */
        class PongWorker extends ActorLQ<Token> {
            {
                listen(this);
            }
/*
            @Override
            protected void initInput() {
                input=new AbstractActor.ScalarInput<Token>();
            }
*/
            @Override
            protected void act(Token token) throws Exception {
                token.sendTo(token.getReplyTo());
                listen(this);
            }
        }
    }

    public static void main(String args[]) throws InterruptedException {
        DemuxPingPongTest nt = new DemuxPingPongTest();
        nt.init();
        nt.testSingle();
    }

}
