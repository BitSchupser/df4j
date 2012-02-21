/*
 * Copyright 2011 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.rfqu.df4j.example;

import java.io.PrintStream;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;

import com.github.rfqu.df4j.core.*;
import com.github.rfqu.df4j.util.MessageSink;


/**
 * A set of identical Actors, passing packets to a randomly selected peer actor.
 * A packet dies after passing predefined number of hops.
 *
 */
public class NetworkTest {
    final static int NUM_ACTORS = 100; // number of nodes
    final static int NR_REQUESTS = NUM_ACTORS * 10; // 100; // number of tokens
    final static int TIME_TO_LIVE = 1000; // hops
    final static int nThreads = Runtime.getRuntime().availableProcessors();
    final static int times = 10;
    PrintStream out = System.out;

    @Before
    public void init() {
        out.println("Network with " + NUM_ACTORS + " nodes, " + NR_REQUESTS + " tokens, with " + TIME_TO_LIVE + " each, on " + nThreads + " threads");
    }

    @Test
    public void test1() throws InterruptedException {
        if (nThreads > 1) {
            out.println(" warning: SimpleExecutorService uses only 1 thread");
        }
        SimpleExecutorService executor = new SimpleExecutorService();
        runTest(executor);
    }

    @Test
    public void test2() throws InterruptedException {
        ThreadFactoryTL tf = new ThreadFactoryTL();
        ExecutorService executor = Executors.newFixedThreadPool(nThreads, tf);
        tf.setExecutor(executor);
        runTest(executor);
    }

	private void runTest(ExecutorService executor) throws InterruptedException {
        String workerName = executor.getClass().getCanonicalName();
        out.println("Using " + workerName);
		Task.setCurrentExecutor(executor);
        for (int i = 0; i < times; i++) {
            runNetwork();
        }
        executor.shutdown();
	}

    /**
     * the type of messages floating between nodes
     */
    static class Packet extends Link {
        int hops_remained;

        public Packet(int hops_remained) {
            this.hops_remained = hops_remained;
        }
    }

    /**
     * The intermediate passing node
     * 
     */
    static class NodeActor extends Actor<Packet> {
        NodeActor[] nodes;
        private final Port<Object> sink;
        private Random rand;

        public NodeActor(long seed, NodeActor[] nodes, Port<Object> sink) {
            this.nodes = nodes;
            this.sink = sink;
            this.rand = new Random(seed);
        }

        /**
         * the method to handle incoming messages for each received packet,
         * decrease the number of remaining hops. If number of hops become zero,
         * send it to sink, otherwise send to another node.
         */
        @Override
        protected void act(Packet token) throws Exception {
            int nextVal = token.hops_remained - 1;
            if (nextVal == 0) {
                sink.send(token);
            } else {
                token.hops_remained = nextVal;
                NodeActor nextNode = nodes[rand.nextInt(nodes.length)];
                nextNode.send(token);
            }
        }

        @Override
        protected void complete() throws Exception {
            // TODO Auto-generated method stub
            
        }
    }

    /**
     * the core of the test
     */
    float runNetwork() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        MessageSink sink = new MessageSink(NR_REQUESTS);
        NodeActor[] nodes = new NodeActor[NUM_ACTORS];
        Random rand = new Random(1);

        // create passing nodes
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new NodeActor(rand.nextLong(), nodes, sink);
        }
        // send packets to random nodes
        for (int k = 0; k < NR_REQUESTS; k++) {
            Actor<Packet> nextInbox = nodes[rand.nextInt(nodes.length)];
            nextInbox.send(new Packet(TIME_TO_LIVE));
        }

        // wait for all packets to die.
        sink.await();

        // report timings
        long etime = (System.currentTimeMillis() - startTime);
        float switchnum = NR_REQUESTS * ((long) TIME_TO_LIVE);
        float delay = etime * 1000 * nThreads / switchnum;
        out.println("Elapsed=" + etime / 1000f + " sec; rate=" + (1 / delay) + " messages/mks/core; mean hop time=" + (delay * 1000) + " ns");
        return delay;
    }

    public static void main(String args[]) throws InterruptedException {
        NetworkTest nt = new NetworkTest();
        nt.init();
        nt.test1();
        nt.test2();
    }

}
