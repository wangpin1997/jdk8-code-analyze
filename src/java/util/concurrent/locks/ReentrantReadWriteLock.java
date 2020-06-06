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

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link ReadWriteLock} supporting similar
 * semantics to {@link ReentrantLock}.
 * <p>This class has the following properties:
 *
 * <ul>
 * <li><b>Acquisition order</b>
 *
 * <p>This class does not impose a reader or writer preference
 * ordering for lock access.  However, it does support an optional
 * <em>fairness</em> policy.
 *
 * <dl>
 * <dt><b><i>Non-fair mode (default)</i></b>
 * <dd>When constructed as non-fair (the default), the order of entry
 * to the read and write lock is unspecified, subject to reentrancy
 * constraints.  A nonfair lock that is continuously contended may
 * indefinitely postpone one or more reader or writer threads, but
 * will normally have higher throughput than a fair lock.
 *
 * <dt><b><i>Fair mode</i></b>
 * <dd>When constructed as fair, threads contend for entry using an
 * approximately arrival-order policy. When the currently held lock
 * is released, either the longest-waiting single writer thread will
 * be assigned the write lock, or if there is a group of reader threads
 * waiting longer than all waiting writer threads, that group will be
 * assigned the read lock.
 *
 * <p>A thread that tries to acquire a fair read lock (non-reentrantly)
 * will block if either the write lock is held, or there is a waiting
 * writer thread. The thread will not acquire the read lock until
 * after the oldest currently waiting writer thread has acquired and
 * released the write lock. Of course, if a waiting writer abandons
 * its wait, leaving one or more reader threads as the longest waiters
 * in the queue with the write lock free, then those readers will be
 * assigned the read lock.
 *
 * <p>A thread that tries to acquire a fair write lock (non-reentrantly)
 * will block unless both the read lock and write lock are free (which
 * implies there are no waiting threads).  (Note that the non-blocking
 * {@link ReadLock#tryLock()} and {@link WriteLock#tryLock()} methods
 * do not honor this fair setting and will immediately acquire the lock
 * if it is possible, regardless of waiting threads.)
 * <p>
 * </dl>
 *
 * <li><b>Reentrancy</b>
 *
 * <p>This lock allows both readers and writers to reacquire read or
 * write locks in the style of a {@link ReentrantLock}. Non-reentrant
 * readers are not allowed until all write locks held by the writing
 * thread have been released.
 *
 * <p>Additionally, a writer can acquire the read lock, but not
 * vice-versa.  Among other applications, reentrancy can be useful
 * when write locks are held during calls or callbacks to methods that
 * perform reads under read locks.  If a reader tries to acquire the
 * write lock it will never succeed.
 *
 * <li><b>Lock downgrading</b>
 * <p>Reentrancy also allows downgrading from the write lock to a read lock,
 * by acquiring the write lock, then the read lock and then releasing the
 * write lock. However, upgrading from a read lock to the write lock is
 * <b>not</b> possible.
 *
 * <li><b>Interruption of lock acquisition</b>
 * <p>The read lock and write lock both support interruption during lock
 * acquisition.
 *
 * <li><b>{@link Condition} support</b>
 * <p>The write lock provides a {@link Condition} implementation that
 * behaves in the same way, with respect to the write lock, as the
 * {@link Condition} implementation provided by
 * {@link ReentrantLock#newCondition} does for {@link ReentrantLock}.
 * This {@link Condition} can, of course, only be used with the write lock.
 *
 * <p>The read lock does not support a {@link Condition} and
 * {@code readLock().newCondition()} throws
 * {@code UnsupportedOperationException}.
 *
 * <li><b>Instrumentation</b>
 * <p>This class supports methods to determine whether locks
 * are held or contended. These methods are designed for monitoring
 * system state, not for synchronization control.
 * </ul>
 *
 * <p>Serialization of this class behaves in the same way as built-in
 * locks: a deserialized lock is in the unlocked state, regardless of
 * its state when serialized.
 *
 * <p><b>Sample usages</b>. Here is a code sketch showing how to perform
 * lock downgrading after updating a cache (exception handling is
 * particularly tricky when handling multiple locks in a non-nested
 * fashion):
 *
 * <pre> {@code
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *
 *   void processCachedData() {
 *     rwl.readLock().lock();
 *     if (!cacheValid) {
 *       // Must release read lock before acquiring write lock
 *       rwl.readLock().unlock();
 *       rwl.writeLock().lock();
 *       try {
 *         // Recheck state because another thread might have
 *         // acquired write lock and changed state before we did.
 *         if (!cacheValid) {
 *           data = ...
 *           cacheValid = true;
 *         }
 *         // Downgrade by acquiring read lock before releasing write lock
 *         rwl.readLock().lock();
 *       } finally {
 *         rwl.writeLock().unlock(); // Unlock write, still hold read
 *       }
 *     }
 *
 *     try {
 *       use(data);
 *     } finally {
 *       rwl.readLock().unlock();
 *     }
 *   }
 * }}</pre>
 * <p>
 * ReentrantReadWriteLocks can be used to improve concurrency in some
 * uses of some kinds of Collections. This is typically worthwhile
 * only when the collections are expected to be large, accessed by
 * more reader threads than writer threads, and entail operations with
 * overhead that outweighs synchronization overhead. For example, here
 * is a class using a TreeMap that is expected to be large and
 * concurrently accessed.
 *
 * <pre> {@code
 * class RWDictionary {
 *   private final Map<String, Data> m = new TreeMap<String, Data>();
 *   private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *   private final Lock r = rwl.readLock();
 *   private final Lock w = rwl.writeLock();
 *
 *   public Data get(String key) {
 *     r.lock();
 *     try { return m.get(key); }
 *     finally { r.unlock(); }
 *   }
 *   public String[] allKeys() {
 *     r.lock();
 *     try { return m.keySet().toArray(); }
 *     finally { r.unlock(); }
 *   }
 *   public Data put(String key, Data value) {
 *     w.lock();
 *     try { return m.put(key, value); }
 *     finally { w.unlock(); }
 *   }
 *   public void clear() {
 *     w.lock();
 *     try { m.clear(); }
 *     finally { w.unlock(); }
 *   }
 * }}</pre>
 *
 * <h3>Implementation Notes</h3>
 *
 * <p>This lock supports a maximum of 65535 recursive write locks
 * and 65535 read locks. Attempts to exceed these limits result in
 * {@link Error} throws from locking methods.
 *
 * @author Doug Lea
 * @since 1.5
 */
//读写锁，因为普通的重入锁是单线程情况，而实际情况是写少读多的场景，所以读写锁应运而生
//它也是实现于可重入锁 ReadWriteLock
public class ReentrantReadWriteLock
        implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;
    /**
     * Inner class providing readlock
     */
    //读锁
    private final ReentrantReadWriteLock.ReadLock readerLock;
    /**
     * Inner class providing writelock
     */
    //写锁
    private final ReentrantReadWriteLock.WriteLock writerLock;
    /**
     * Performs all synchronization mechanics
     */
    //内部维护一个继承AQS的类
    final Sync sync;

    /**
     * Creates a new {@code ReentrantReadWriteLock} with
     * default (nonfair) ordering properties.
     */
    //还是默认非公平锁
    public ReentrantReadWriteLock() {
        this(false);
    }

    /**
     * Creates a new {@code ReentrantReadWriteLock} with
     * the given fairness policy.
     *
     * @param fair {@code true} if this lock should use a fair ordering policy
     */
    //自定义默认是公平锁还是非公平锁
    //然后实例化读锁和写锁
    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);
        writerLock = new WriteLock(this);
    }

    //返回写锁对象
    public ReentrantReadWriteLock.WriteLock writeLock() {
        return writerLock;
    }
    //返回读锁对象
    public ReentrantReadWriteLock.ReadLock readLock() {
        return readerLock;
    }

    /**
     * Synchronization implementation for ReentrantReadWriteLock.
     * Subclassed into fair and nonfair versions.
     */
    //内部维护的Sync类，继承自AQS
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 6317671515068378041L;

        // 下面这块说的就是将 state 一分为二，高 16 位用于共享模式，低16位用于独占模式
        static final int SHARED_SHIFT = 16;
        //共享锁状态单位值  65536
        static final int SHARED_UNIT = (1 << SHARED_SHIFT);
        //共享锁线程最大个数
        static final int MAX_COUNT = (1 << SHARED_SHIFT) - 1;
        //排它锁掩码 65535 二进制就是15个1   111-1111-1111-1111
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /**
         * Returns the number of shared holds represented in count
         */
        // 取 c 的高 16 位值，代表读锁的获取次数/线程数(包括重入)
        static int sharedCount(int c) {
            return c >>> SHARED_SHIFT;
        }

        /**
         * Returns the number of exclusive holds represented in count
         */
        // 取 c 的低 16 位值，代表写锁的重入次数，因为写锁是独占模式
        static int exclusiveCount(int c) {
            return c & EXCLUSIVE_MASK;
        }

        /**
         * A counter for per-thread read hold counts.
         * Maintained as a ThreadLocal; cached in cachedHoldCounter
         */
        // 这个嵌套类的实例用来记录每个线程持有的读锁数量(读锁重入)
        static final class HoldCounter {
            //持有的读锁数量
            int count = 0;
            // Use id, not reference, to avoid garbage retention
            //当前线程id
            final long tid = getThreadId(Thread.currentThread());
        }

        /**
         * ThreadLocal subclass. Easiest to explicitly define for sake
         * of deserialization mechanics.
         */
        // ThreadLocal 的子类
        static final class ThreadLocalHoldCounter
                extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        /**
         * The number of reentrant read locks held by current thread.
         * Initialized only in constructor and readObject.
         * Removed whenever a thread's read hold count drops to 0.
         */
        //组合使用上面两个类，用一个 ThreadLocal 来记录当前线程持有的读锁数量
        private transient ThreadLocalHoldCounter readHolds;

        /**
         * The hold count of the last thread to successfully acquire
         * readLock. This saves ThreadLocal lookup in the common case
         * where the next thread to release is the last one to
         * acquire. This is non-volatile since it is just used
         * as a heuristic, and would be great for threads to cache.
         *
         * <p>Can outlive the Thread for which it is caching the read
         * hold count, but avoids garbage retention by not retaining a
         * reference to the Thread.
         *
         * <p>Accessed via a benign data race; relies on the memory
         * model's final field and out-of-thin-air guarantees.
         */
        // 用于缓存，记录"最后一个获取读锁的线程"的读锁重入次数，
        // 所以不管哪个线程获取到读锁后，就把这个值占为已用，这样就不用到 ThreadLocal 中查询 map 了
        // 算不上理论的依据：通常读锁的获取很快就会伴随着释放，
        //   显然，在 获取->释放 读锁这段时间，如果没有其他线程获取读锁的话，此缓存就能帮助提高性能
        private transient HoldCounter cachedHoldCounter;


        // 第一个获取读锁的线程(并且其未释放读锁)，以及它持有的读锁数量
        private transient Thread firstReader = null;

        //用来记录第一个获取读锁的线程获取读锁的可重入次数
        private transient int firstReaderHoldCount;

        Sync() {
            readHolds = new ThreadLocalHoldCounter();
            // 为了保证 readHolds 的内存可见性
            setState(getState()); // ensures visibility of readHolds
        }

        /*
         * Acquires and releases use the same code for fair and
         * nonfair locks, but differ in whether/how they allow barging
         * when queues are non-empty.
         */

        /**
         * Returns true if the current thread, when trying to acquire
         * the read lock, and otherwise eligible to do so, should block
         * because of policy for overtaking other waiting threads.
         */
        abstract boolean readerShouldBlock();

        /**
         * Returns true if the current thread, when trying to acquire
         * the write lock, and otherwise eligible to do so, should block
         * because of policy for overtaking other waiting threads.
         */
        abstract boolean writerShouldBlock();

        /*
         * Note that tryRelease and tryAcquire can be called by
         * Conditions. So it is possible that their arguments contain
         * both read and write holds that are all released during a
         * condition wait and re-established in tryAcquire.
         */
        //独占模式的释放锁
        protected final boolean tryRelease(int releases) {
            //判断当前线程是否是写锁拥有者，不是就抛异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            //获取可重入值，因为获取写锁的时候读锁的状态值肯定位0，这里不需要考虑高16位
            int nextc = getState() - releases;
            //如果可重入值为0则释放,返回true，否则就是减少状态，返回false
            boolean free = exclusiveCount(nextc) == 0;
            if (free)
                setExclusiveOwnerThread(null);
            setState(nextc);
            return free;
        }

        //写锁 lock()--->AQS acquire()-->这里
        protected final boolean tryAcquire(int acquires) {
            //获取当前线程
            Thread current = Thread.currentThread();
            //拿到线程状态
            int c = getState();
            //取状态的低16位运算结果，如 acquires&111-1111-1111-1111，所以一般重入次数不超过65535的话等于本身
            int w = exclusiveCount(c);
            //如果线程状态不为0，说明被持有了
            if (c != 0) {
                // (Note: if c != 0 and w == 0 then shared count != 0)
                //w==0,说明c大于2>>17，那就到了状态的高16位了，说明有其他线程获取了读锁
                //current != getExclusiveOwnerThread(),当前写锁不是本身，就返回，获取失败
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                //如果线程个数超过了最大的65535，抛出错误
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                // Reentrant acquire
                //状态+acquires 重入
                setState(c + acquires);
                return true;
            }
            //如果上面的if没进
            //第一个线程获取写锁，非公平锁writerShouldBlock()总是false
            //公平锁就是看当前线程有没有前驱节点 ，有就放弃，返回false
            //就看CAS修改状态是否成功，失败返回false
            if (writerShouldBlock() ||
                    !compareAndSetState(c, c + acquires))
                return false;
            //设置成自身线程拥有写锁
            setExclusiveOwnerThread(current);
            return true;
        }

        //释放共享锁
        protected final boolean tryReleaseShared(int unused) {
            //拿到当前线程
            Thread current = Thread.currentThread();
            //判断当前线程释放第一个，是就gc
            if (firstReader == current) {
                // assert firstReaderHoldCount > 0;
                if (firstReaderHoldCount == 1)
                    firstReader = null;
                else
                    firstReaderHoldCount--;
            } else {
                HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                int count = rh.count;
                if (count <= 1) {
                    readHolds.remove();
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                --rh.count;
            }
            //循环知道state更新为0，即释放了所有的重入
            for (; ; ) {
                int c = getState();
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc))
                    // Releasing the read lock has no effect on readers,
                    // but it may allow waiting writers to proceed if
                    // both read and write locks are now free.
                    return nextc == 0;
            }
        }

        private IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException(
                    "attempt to unlock read lock, not locked by current thread");
        }

        //从aqs中的acquireShared方法跳到AQS中的tryAcquireShared在跳到这具体的实现
        protected final int tryAcquireShared(int unused) {
            //拿到当前线程
            Thread current = Thread.currentThread();
            //获取线程状态
            int c = getState();
            // exclusiveCount(c) 不等于 0，说明有线程持有写锁，
            //    而且不是当前线程持有写锁，那么当前线程获取读锁失败
            //         （另，如果持有写锁的是当前线程，是可以继续获取读锁的）
            if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                return -1;
            // 读锁的获取次数
            int r = sharedCount(c);
            // 读锁获取是否需要被阻塞，稍后细说。为了进去下面的分支，假设这里不阻塞就好了
            if (!readerShouldBlock() &&
                    // 判断是否会溢出 (2^16-1，没那么容易溢出的)
                    r < MAX_COUNT &&
                    // 下面这行 CAS 是将 state 属性的高 16 位加 1，低 16 位不变，如果成功就代表获取到了读锁
                    compareAndSetState(c, c + SHARED_UNIT)) {
                //   进到这里就是获取到了读锁
                if (r == 0) {
                    // r == 0 说明此线程是第一个获取读锁的，或者说在它前面获取读锁的都走光光了，它也算是第一个吧
                    //  记录 firstReader 为当前线程，及其持有的读锁数量：1
                    firstReader = current;
                    firstReaderHoldCount = 1;
                } else if (firstReader == current) {
                    // 进来这里，说明是 firstReader 重入获取读锁（这非常简单，count 加 1 结束）
                    firstReaderHoldCount++;
                } else {
                    // 前面我们说了 cachedHoldCounter 用于缓存最后一个获取读锁的线程
                    // 如果 cachedHoldCounter 缓存的不是当前线程，设置为缓存当前线程的 HoldCounter
                    HoldCounter rh = cachedHoldCounter;
                    if (rh == null || rh.tid != getThreadId(current))
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)
                        // 到这里，那么就是 cachedHoldCounter 缓存的是当前线程，但是 count 为 0，
                        // 大家可以思考一下：这里为什么要 set ThreadLocal 呢？(当然，答案肯定不在这块代码中)
                        //   既然 cachedHoldCounter 缓存的是当前线程，
                        //   当前线程肯定调用过 readHolds.get() 进行初始化 ThreadLocal
                        readHolds.set(rh);
                    rh.count++;
                }
                // return 大于 0 的数，代表获取到了共享锁
                return 1;
            }
            //没进去上面的if,就在这不停的cas尝试
            return fullTryAcquireShared(current);
        }

        /**
         * Full version of acquire for reads, that handles CAS misses
         * and reentrant reads not dealt with in tryAcquireShared.
         */
        //通过CAS不停尝试获取锁
        final int fullTryAcquireShared(Thread current) {

            HoldCounter rh = null;
            for (; ; ) {
                int c = getState();
                if (exclusiveCount(c) != 0) {
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                    // else we hold the exclusive lock; blocking here
                    // would cause deadlock.
                } else if (readerShouldBlock()) {
                    // Make sure we're not acquiring read lock reentrantly
                    if (firstReader == current) {
                        // assert firstReaderHoldCount > 0;
                    } else {
                        if (rh == null) {
                            rh = cachedHoldCounter;
                            if (rh == null || rh.tid != getThreadId(current)) {
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    readHolds.remove();
                            }
                        }
                        if (rh.count == 0)
                            return -1;
                    }
                }
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (sharedCount(c) == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }

        /**
         * Performs tryLock for write, enabling barging in both modes.
         * This is identical in effect to tryAcquire except for lack
         * of calls to writerShouldBlock.
         */
        //获取写锁
        final boolean tryWriteLock() {
            //拿到当前线程
            Thread current = Thread.currentThread();
            //拿到线程状态
            int c = getState();
            //状态不为0说明被持有了
            if (c != 0) {
                //拿到低16位运算结果
                int w = exclusiveCount(c);
                //w==0，说明低16位为0，而state！=0，说明了有线程持有了读锁
                //w!=0,说明有线程持有了写锁
                //current != getExclusiveOwnerThread()持有写锁的不是本身
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                //超过了最大线程，抛出错误
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }
            //如果没有进if,说明当前没有线程持有锁，通过CAS修改线程状态，
            // 为什么要CAS.因为此时可能有多个线程在此，为了保证其原子性
            if (!compareAndSetState(c, c + 1))
                return false;
            //把持有锁的线程置为本身，然后返回true
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * Performs tryLock for read, enabling barging in both modes.
         * This is identical in effect to tryAcquireShared except for
         * lack of calls to readerShouldBlock.
         */
        final boolean tryReadLock() {
            Thread current = Thread.currentThread();
            for (; ; ) {
                int c = getState();
                if (exclusiveCount(c) != 0 &&
                        getExclusiveOwnerThread() != current)
                    return false;
                int r = sharedCount(c);
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (r == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                    }
                    return true;
                }
            }
        }

        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // Methods relayed to outer class

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            // Must read state before owner to ensure memory consistency
            return ((exclusiveCount(getState()) == 0) ?
                    null :
                    getExclusiveOwnerThread());
        }

        final int getReadLockCount() {
            return sharedCount(getState());
        }

        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }

        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }

        final int getReadHoldCount() {
            if (getReadLockCount() == 0)
                return 0;

            Thread current = Thread.currentThread();
            if (firstReader == current)
                return firstReaderHoldCount;

            HoldCounter rh = cachedHoldCounter;
            if (rh != null && rh.tid == getThreadId(current))
                return rh.count;

            int count = readHolds.get().count;
            if (count == 0) readHolds.remove();
            return count;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
                throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // reset to unlocked state
        }

        final int getCount() {
            return getState();
        }
    }

    /**
     * Nonfair version of Sync
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;

        final boolean writerShouldBlock() {
            return false; // writers can always barge
        }

        //判断条件队列中是否存在元素，且判断是否在尝试获取写锁
        final boolean readerShouldBlock() {

            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /**
     * Fair version of Sync
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;

        final boolean writerShouldBlock() {
            return hasQueuedPredecessors();
        }

        final boolean readerShouldBlock() {
            return hasQueuedPredecessors();
        }
    }

    /**
     * The lock returned by method {@link ReentrantReadWriteLock#readLock}.
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        //构造方法，通过外部来调用
        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        //获取读锁（共享锁），跳到AQS中
        public void lock() {
            sync.acquireShared(1);
        }


        //跟lock方法一样，不同之处就是他会中断进行响应
        //也就是其他线程调用了Interrupt()方法来中断了当前线程时，当前线程会抛出InterruptedException异常
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        //获取读锁，如果当前没有其他线程获取写锁，那就会获取读锁成功
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        //带超时时间的读锁，如果尝试获取读锁失败，那么就会挂起timeout指定时长
        //恢复后重新获取读锁 ，没成功就返回false
        //挂起（睡眠）时间，被打断则抛异常
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        //释放读锁
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         * Throws {@code UnsupportedOperationException} because
         * {@code ReadLocks} do not support conditions.
         *
         * @throws UnsupportedOperationException always
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns a string identifying this lock, as well as its lock state.
         * The state, in brackets, includes the String {@code "Read locks ="}
         * followed by the number of held read locks.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                    "[Read locks = " + r + "]";
        }
    }

    /**
     * The lock returned by method {@link ReentrantReadWriteLock#writeLock}.
     */
    //写锁
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }


        //写锁获取锁，跳转到父类AQS了
        public void lock() {
            sync.acquire(1);
        }


        //跟lock方法一样，不同之处就是他会中断进行响应
        //也就是其他线程调用了Interrupt()方法来中断了当前线程时，当前线程会抛出InterruptedException异常
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        //尝试获取写锁
        public boolean tryLock() {
            return sync.tryWriteLock();
        }


        //尝试获取写锁，带上了超时时间，如果尝试获取锁失败，就会挂起timeout长时间
        //待超时时间过后，当前线程被激活，如果还是没有获取到锁，就返回false
        //在挂起的超时时间内，如果被其他线程调用interrupt()方法中断，那么会怕抛出中断异常
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         * Attempts to release this lock.
         *
         * <p>If the current thread is the holder of this lock then
         * the hold count is decremented. If the hold count is now
         * zero then the lock is released.  If the current thread is
         * not the holder of this lock then {@link
         * IllegalMonitorStateException} is thrown.
         *
         * @throws IllegalMonitorStateException if the current thread does not
         *                                      hold this lock
         */
        //释放锁，状态-1，如果没锁而调用了就报状态监控异常
        public void unlock() {
            sync.release(1);
        }

        public Condition newCondition() {
            return sync.newCondition();
        }

        /**
         * Returns a string identifying this lock, as well as its lock
         * state.  The state, in brackets includes either the String
         * {@code "Unlocked"} or the String {@code "Locked by"}
         * followed by the {@linkplain Thread#getName name} of the owning thread.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ?
                    "[Unlocked]" :
                    "[Locked by thread " + o.getName() + "]");
        }

        /**
         * Queries if this write lock is held by the current thread.
         * Identical in effect to {@link
         * ReentrantReadWriteLock#isWriteLockedByCurrentThread}.
         *
         * @return {@code true} if the current thread holds this lock and
         * {@code false} otherwise
         * @since 1.6
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         * Queries the number of holds on this write lock by the current
         * thread.  A thread has a hold on a lock for each lock action
         * that is not matched by an unlock action.  Identical in effect
         * to {@link ReentrantReadWriteLock#getWriteHoldCount}.
         *
         * @return the number of holds on this lock by the current thread,
         * or zero if this lock is not held by the current thread
         * @since 1.6
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    // Instrumentation and status

    /**
     * Returns {@code true} if this lock has fairness set true.
     *
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * Returns the thread that currently owns the write lock, or
     * {@code null} if not owned. When this method is called by a
     * thread that is not the owner, the return value reflects a
     * best-effort approximation of current lock status. For example,
     * the owner may be momentarily {@code null} even if there are
     * threads trying to acquire the lock but have not yet done so.
     * This method is designed to facilitate construction of
     * subclasses that provide more extensive lock monitoring
     * facilities.
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     *
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring system state, not for
     * synchronization control.
     *
     * @return {@code true} if any thread holds the write lock and
     * {@code false} otherwise
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * Queries if the write lock is held by the current thread.
     *
     * @return {@code true} if the current thread holds the write lock and
     * {@code false} otherwise
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * Queries the number of reentrant write holds on this lock by the
     * current thread.  A writer thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the write lock by the current thread,
     * or zero if the write lock is not held by the current thread
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * Queries the number of reentrant read holds on this lock by the
     * current thread.  A reader thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the read lock by the current thread,
     * or zero if the read lock is not held by the current thread
     * @since 1.6
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the write lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the read lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting to acquire the read or
     * write lock. Note that because cancellations may occur at any
     * time, a {@code true} return does not guarantee that any other
     * thread will ever acquire a lock.  This method is designed
     * primarily for use in monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     * acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire either
     * the read or write lock. Note that because cancellations may
     * occur at any time, a {@code true} return does not guarantee
     * that this thread will ever acquire a lock.  This method is
     * designed primarily for use in monitoring of the system state.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire
     * either the read or write lock.  The value is only an estimate
     * because the number of threads may change dynamically while this
     * method traverses internal data structures.  This method is
     * designed for use in monitoring of the system state, not for
     * synchronization control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire either the read or write lock.  Because the actual set
     * of threads may change dynamically while constructing this
     * result, the returned collection is only a best-effort estimate.
     * The elements of the returned collection are in no particular
     * order.  This method is designed to facilitate construction of
     * subclasses that provide more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * 条件队列中是否还有后继节点
     * @param condition
     * @return
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    //获取等待队列的长度
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject) condition);
    }


    //获取等待中的线程
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() +
                "[Write locks = " + w + ", Read locks = " + r + "]";
    }

    //获取线程id
    static final long getThreadId(Thread thread) {
        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    //线程id的偏移量
    private static final long TID_OFFSET;

    //通过unsafe类获取Thread的几个类属性
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            TID_OFFSET = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("tid"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
