/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.*;

/**
 * Provides default implementations of {@link ExecutorService}
 * execution methods. This class implements the {@code submit},
 * {@code invokeAny} and {@code invokeAll} methods using a
 * {@link RunnableFuture} returned by {@code newTaskFor}, which defaults
 * to the {@link FutureTask} class provided in this package.  For example,
 * the implementation of {@code submit(Runnable)} creates an
 * associated {@code RunnableFuture} that is executed and
 * returned. Subclasses may override the {@code newTaskFor} methods
 * to return {@code RunnableFuture} implementations other than
 * {@code FutureTask}.
 *
 * <p><b>Extension example</b>. Here is a sketch of a class
 * that customizes {@link ThreadPoolExecutor} to use
 * a {@code CustomTask} class instead of the default {@code FutureTask}:
 *  <pre> {@code
 * public class CustomThreadPoolExecutor extends ThreadPoolExecutor {
 *
 *   static class CustomTask<V> implements RunnableFuture<V> {...}
 *
 *   protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
 *       return new CustomTask<V>(c);
 *   }
 *   protected <V> RunnableFuture<V> newTaskFor(Runnable r, V v) {
 *       return new CustomTask<V>(r, v);
 *   }
 *   // ... add constructors, etc.
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractExecutorService implements ExecutorService {

    /**
     * Returns a {@code RunnableFuture} for the given runnable and default
     * value.
     *
     * @param runnable the runnable task being wrapped
     * @param value the default value for the returned future
     * @param <T> the type of the given value
     * @return a {@code RunnableFuture} which, when run, will run the
     * underlying runnable and which, as a {@code Future}, will yield
     * the given value as its result and provide for cancellation of
     * the underlying task
     * @since 1.6
     */
    // RunnableFuture 是用于获取执行结果的，我们常用它的子类 FutureTask
    // newTaskFor 方法用于将我们的任务包装成 FutureTask 提交到线程池中执行
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTask<T>(runnable, value);
    }

    /**
     * Returns a {@code RunnableFuture} for the given callable task.
     *
     * @param callable the callable task being wrapped
     * @param <T> the type of the callable's result
     * @return a {@code RunnableFuture} which, when run, will call the
     * underlying callable and which, as a {@code Future}, will yield
     * the callable's result as its result and provide for
     * cancellation of the underlying task
     * @since 1.6
     */
    // newTaskFor 方法用于将我们的任务包装成 FutureTask 提交到线程池中执行
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTask<T>(callable);
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    //提交任务
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        //将任务包装成FutureTask
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        // 2. 交给执行器执行，execute 方法由具体的子类来实现(模板方法模式)
        // 前面也说了，FutureTask 间接实现了Runnable 接口。
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        //包装成FutureTask
        RunnableFuture<T> ftask = newTaskFor(task, result);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    //同上
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task);
        execute(ftask);
        return ftask;
    }

    /**
     * the main mechanics of invokeAny.
     */
    // 此方法目的：将 tasks 集合中的任务提交到线程池执行，任意一个线程执行完后就可以结束了
    // 第二个参数 timed 代表是否设置超时机制，超时时间为第三个参数，
    // 如果 timed 为 true，同时超时了还没有一个线程返回结果，那么抛出 TimeoutException 异常
    private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks,
                              boolean timed, long nanos)
        throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null)
            throw new NullPointerException();
        //任务数
        int ntasks = tasks.size();
        if (ntasks == 0)
            throw new IllegalArgumentException();
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(ntasks);
        // ExecutorCompletionService 不是一个真正的执行器，参数 this 才是真正的执行器
        // 它对执行器进行了包装，每个任务结束后，将结果保存到内部的一个 completionQueue 队列中
        // 这也是为什么这个类的名字里面有个 Completion 的原因吧。
        ExecutorCompletionService<T> ecs =
            new ExecutorCompletionService<T>(this);

        // For efficiency, especially in executors with limited
        // parallelism, check to see if previously submitted tasks are
        // done before submitting more of them. This interleaving
        // plus the exception mechanics account for messiness of main
        // loop.

        try {
            // Record exceptions so that if we fail to obtain any
            // result, we can throw the last exception we got.
            // 用于保存异常信息，此方法如果没有得到任何有效的结果，那么我们可以抛出最后得到的一个异常
            ExecutionException ee = null;
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Iterator<? extends Callable<T>> it = tasks.iterator();

            // Start one task for sure; the rest incrementally
            // 首先先提交一个任务，后面的任务到下面的 for 循环一个个提交
            futures.add(ecs.submit(it.next()));
            --ntasks;
            // 正在执行的任务数(提交的时候 +1，任务结束的时候 -1)
            int active = 1;

            for (;;) {
                // ecs 上面说了，其内部有一个 completionQueue 用于保存执行完成的结果
                // BlockingQueue 的 poll 方法不阻塞，返回 null 代表队列为空
                Future<T> f = ecs.poll();
                // 为 null，说明刚刚提交的第一个线程还没有执行完成
                // 在前面先提交一个任务，加上这里做一次检查，也是为了提高性能
                if (f == null) {
                    if (ntasks > 0) {
                        --ntasks;
                        futures.add(ecs.submit(it.next()));
                        ++active;
                    }
                    // 这里是 else if，不是 if。这里说明，没有任务了，同时 active 为 0 说明
                    // 任务都执行完成了。其实我也没理解为什么这里做一次 break？
                    // 因为我认为 active 为 0 的情况，必然从下面的 f.get() 返回了
                    else if (active == 0)
                        break;
                    // 这里也是 else if。这里说的是，没有任务了，但是设置了超时时间，这里检测是否超时
                    else if (timed) {
                        // 带等待的 poll 方法
                        f = ecs.poll(nanos, TimeUnit.NANOSECONDS);
                        // 如果已经超时，抛出 TimeoutException 异常，这整个方法就结束了
                        if (f == null)
                            throw new TimeoutException();
                        nanos = deadline - System.nanoTime();
                    }
                    // 这里是 else。说明，没有任务需要提交，但是池中的任务没有完成，还没有超时(如果设置了超时)
                    // take() 方法会阻塞，直到有元素返回，说明有任务结束了
                    else
                        f = ecs.take();
                }
                // 有任务结束了
                if (f != null) {
                    --active;
                    try {
                        // 返回执行结果，如果有异常，都包装成 ExecutionException
                        return f.get();
                    } catch (ExecutionException eex) {
                        ee = eex;
                    } catch (RuntimeException rex) {
                        ee = new ExecutionException(rex);
                    }
                }
            }// 注意看 for 循环的范围，一直到这里

            if (ee == null)
                ee = new ExecutionException();
            throw ee;

        } finally {
            // 方法退出之前，取消其他的任务
            for (int i = 0, size = futures.size(); i < size; i++)
                futures.get(i).cancel(true);
        }
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        try {
            return doInvokeAny(tasks, false, 0);
        } catch (TimeoutException cannotHappen) {
            assert false;
            return null;
        }
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return doInvokeAny(tasks, true, unit.toNanos(timeout));
    }

    // 执行所有的任务，返回任务结果。
    // 先不要看这个方法，我们先想想，其实我们自己提交任务到线程池，也是想要线程池执行所有的任务
    // 只不过，我们是每次 submit 一个任务，这里以一个集合作为参数提交
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                //包装成FutureTask
                RunnableFuture<T> f = newTaskFor(t);
                futures.add(f);
                //执行任务
                execute(f);
            }
            for (int i = 0, size = futures.size(); i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    try {
                        // 这是一个阻塞方法，直到获取到值，或抛出了异常
                        // 这里有个小细节，其实 get 方法签名上是会抛出 InterruptedException 的
                        // 可是这里没有进行处理，而是抛给外层去了。此异常发生于还没执行完的任务被取消了
                        f.get();
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    }
                }
            }
            done = true;
            // 这个方法返回，不像其他的场景，返回 List<Future>，其实执行结果还没出来
            // 这个方法返回是真正的返回，任务都结束了
            return futures;
        } finally {
            // 为什么要这个？就是上面说的有异常的情况
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(true);
        }
    }
    // 带超时的 invokeAll
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks)
                futures.add(newTaskFor(t));

            final long deadline = System.nanoTime() + nanos;
            final int size = futures.size();

            // Interleave time checks and calls to execute in case
            // executor doesn't have any/much parallelism.
            // 提交一个任务，检测一次是否超时
            for (int i = 0; i < size; i++) {
                execute((Runnable)futures.get(i));
                nanos = deadline - System.nanoTime();
                //超时
                if (nanos <= 0L)
                    return futures;
            }

            for (int i = 0; i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    if (nanos <= 0L)
                        return futures;
                    try {
                        // 调用带超时的 get 方法，这里的参数 nanos 是剩余的时间，
                        // 因为上面其实已经用掉了一些时间了
                        f.get(nanos, TimeUnit.NANOSECONDS);
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    } catch (TimeoutException toe) {
                        return futures;
                    }
                    nanos = deadline - System.nanoTime();
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(true);
        }
    }

}
