package com.github.rfqu.df4j.util;

import java.util.concurrent.CountDownLatch;

import com.github.rfqu.df4j.core.OutPort;

/**
 * The ending node. This is Port rather than actor, to be accessed outside
 * the actor world.
 */
public class MessageSink extends CountDownLatch implements OutPort<Object> {

    public MessageSink(int count) {
        super(count);
    }

    @Override
    public void send(Object message) {
        super.countDown();
    }

}