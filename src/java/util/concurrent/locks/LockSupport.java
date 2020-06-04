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

package java.util.concurrent.locks;

/**
 * <p><b>Sample Usage.</b> Here is a sketch of a first-in-first-out
 * non-reentrant lock class:
 * <pre> {@code
 * class FIFOMutex {
 *   private final AtomicBoolean locked = new AtomicBoolean(false);
 *   private final Queue<Thread> waiters
 *     = new ConcurrentLinkedQueue<Thread>();
 *
 *   public void lock() {
 *     boolean wasInterrupted = false;
 *     Thread current = Thread.currentThread();
 *     waiters.add(current);
 *
 *     // Block while not first in queue or cannot acquire lock
 *     while (waiters.peek() != current ||
 *            !locked.compareAndSet(false, true)) {
 *       LockSupport.park(this);
 *       if (Thread.interrupted()) // ignore interrupts while waiting
 *         wasInterrupted = true;
 *     }
 *
 *     waiters.remove();
 *     if (wasInterrupted)          // reassert interrupt status on exit
 *       current.interrupt();
 *   }
 *
 *   public void unlock() {
 *     locked.set(false);
 *     LockSupport.unpark(waiters.peek());
 *   }
 * }}</pre>
 */
//该类的主要作用是挂起和唤醒线程，该类是创建所和其他同步类的基础
public class LockSupport {

    //私有构造，不能被实例化
    private LockSupport() {
    } // Cannot be instantiated.

    /**
     * 修改当前线程的挂起偏移量
     * @param t
     * @param arg
     */
    private static void setBlocker(Thread t, Object arg) {
        // Even though volatile, hotspot doesn't need a write barrier here.
        UNSAFE.putObject(t, parkBlockerOffset, arg);
    }

    /**
     * Makes available the permit for the given thread, if it
     * was not already available.  If the thread was blocked on
     * {@code park} then it will unblock.  Otherwise, its next call
     * to {@code park} is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * thread has not been started.
     *
     * @param thread the thread to unpark, or {@code null}, in which case
     *               this operation has no effect
     */
    //中断挂起
    public static void unpark(Thread thread) {
        if (thread != null)
            UNSAFE.unpark(thread);
    }


    //线程挂起，会被unpark或者线程中断而中断
    //blocker:用来记录导致线程阻塞的阻塞对象，方便问题排查
    public static void park(Object blocker) {
        //获取当前线程
        Thread t = Thread.currentThread();
        //设置blocker
        setBlocker(t, blocker);
        //获取许可
        UNSAFE.park(false, 0L);
        //重新设置blocker
        setBlocker(t, null);
        //为何两个设置blocker，因为当线程park方法时，设置好blocker了，调用unsafe.park时，已经阻塞了
        //但是当unpark方法被调用了，方法就会继续执行，相当于就是还原了
    }

    //同上，增加最长阻塞时间不超过 nanos纳秒
    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            UNSAFE.park(false, nanos);
            setBlocker(t, null);
        }
    }

    //挂起到指定的时间
    public static void parkUntil(Object blocker, long deadline) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        UNSAFE.park(true, deadline);
        setBlocker(t, null);
    }

    //获取blocker参数
    public static Object getBlocker(Thread t) {
        if (t == null)
            throw new NullPointerException();
        return UNSAFE.getObjectVolatile(t, parkBlockerOffset);
    }

    //挂起，无参
    public static void park() {
        UNSAFE.park(false, 0L);
    }


    //直接挂起多长时间
    public static void parkNanos(long nanos) {
        if (nanos > 0)
            UNSAFE.park(false, nanos);
    }


    //直接挂起，直到deadline
    public static void parkUntil(long deadline) {
        UNSAFE.park(true, deadline);
    }

    /**
     * Returns the pseudo-randomly initialized or updated secondary seed.
     * Copied from ThreadLocalRandom due to package access restrictions.
     */
    static final int nextSecondarySeed() {
        int r;
        Thread t = Thread.currentThread();
        if ((r = UNSAFE.getInt(t, SECONDARY)) != 0) {
            r ^= r << 13;   // xorshift
            r ^= r >>> 17;
            r ^= r << 5;
        } else if ((r = java.util.concurrent.ThreadLocalRandom.current().nextInt()) == 0)
            r = 1; // avoid zero
        UNSAFE.putInt(t, SECONDARY, r);
        return r;
    }

    // Hotspot implementation via intrinsics API
    private static final sun.misc.Unsafe UNSAFE;
    private static final long parkBlockerOffset;
    private static final long SEED;
    private static final long PROBE;
    private static final long SECONDARY;

    static {
        //获取unsafe实例和 Thread类中ThreadLocalRandom的一些属性的偏移量和parkBlocker
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            parkBlockerOffset = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("parkBlocker"));
            SEED = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomSeed"));
            PROBE = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomProbe"));
            SECONDARY = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomSecondarySeed"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

}
