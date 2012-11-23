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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base class for dataflow nodes (including Actors).
 * Features:
 *  - Task is Runnable - can be submitted to an Executor
 *  - Task holds default executor, which was set at the moment of creation:
 *  either explicitly, or implicitly via thread-local current executor.
 *  If executor is null, firing executes immediately on the thread which fires the task.
 *  - Task is Link, and can be enqueued in the standard LinkedQueue, e.g can be sent 
 *  to Actors and Ports.
 * Static part of this class holds thread-local current executor machinery.
 *   
 * @author kaigorodov
 *
 */
public abstract class Task extends Link implements Runnable {
    protected final Executor executor;

    public Task(Executor executor) {
        this.executor = executor;
    }

    public Task() {
        this(getCurrentExecutor());
    }

    /**
     * activates this task by sending it to the executor
     */
    protected final void fire() {
        if (executor==null) {
            run();
        } else {
            executor.execute(this);
        }
    }

    /**
     * @return current executor stored in thread-local variable
     */
    public static Executor getCurrentExecutor() {
        return DFContext.getCurrentExecutor();
    }

    public static ExecutorService getCurrentExecutorService() {
        Executor executor=getCurrentExecutor();
        ExecutorService service;
        if (executor instanceof ExecutorService) {
            service=(ExecutorService)executor;
        } else {
            service=new PrimitiveExecutorService(executor);
        }
        return service;
    }

    /**
     * Waits for currently started tasks to finish.
     * Invoke before exiting main thread, or otherwise
     * thread pool with daemon threads would break execution
     * of the not finished tasks.
     */
    public static void completeCurrentExecutorService() {
        Executor executor=getCurrentExecutor();
        ExecutorService service;
        if (!(executor instanceof ExecutorService)) {
            throw new IllegalStateException("current Executor is not a ExecutorService.");
        }
        service=(ExecutorService)executor;
        service.shutdown();
        try {
            service.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        service.shutdownNow();
    }

    static class PrimitiveExecutorService implements ExecutorService {
        static final String message = "PrimitiveExecutor not a service";
        protected final Executor executor;

        public PrimitiveExecutorService(Executor executor) {
            this.executor = executor;
        }

        /**
         * Executes the given command at some time in the future.
         * 
         * @param command the runnable
         * @throws NullPointerException if command is null
         */
        @Override
        public void execute(Runnable command) {
            executor.execute(command);
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException(message);
            
        }

        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public boolean isShutdown() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
                throws InterruptedException {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public <T> List<Future<T>> invokeAll(
                Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException {
            throw new UnsupportedOperationException(message);
        }

    }

}