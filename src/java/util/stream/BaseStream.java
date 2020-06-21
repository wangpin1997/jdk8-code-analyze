/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Base interface for streams, which are sequences of elements supporting
 * sequential and parallel aggregate operations.  The following example
 * illustrates an aggregate operation using the stream types {@link Stream}
 * and {@link IntStream}, computing the sum of the weights of the red widgets:
 *
 * <pre>{@code
 *     int sum = widgets.stream()
 *                      .filter(w -> w.getColor() == RED)
 *                      .mapToInt(w -> w.getWeight())
 *                      .sum();
 * }</pre>
 *
 * See the class documentation for {@link Stream} and the package documentation
 * for <a href="package-summary.html">java.util.stream</a> for additional
 * specification of streams, stream operations, stream pipelines, and
 * parallelism, which governs the behavior of all stream types.
 *
 * @param <T> the type of the stream elements
 * @param <S> the type of of the stream implementing {@code BaseStream}
 * @since 1.8
 * @see Stream
 * @see IntStream
 * @see LongStream
 * @see DoubleStream
 * @see <a href="package-summary.html">java.util.stream</a>
 */
//流操作的最顶层接口
// T代表指定stream元素的类型，S代表stream的实现类
public interface BaseStream<T, S extends BaseStream<T, S>>
        extends AutoCloseable {

    //迭代方法
    Iterator<T> iterator();


    //跟迭代方法类似，到那时返回的对象是Spliterator，他可以将元素分割成多份，分别交于不同的线程。提高效率
    Spliterator<T> spliterator();


    //是否为并行流
    boolean isParallel();


    //返回一个串行流，如果流本身就是串行的，就返回其本身
    S sequential();


    //返回一个并行流，如果流本身是并行的，就返回其本身
    S parallel();


    //返回一个无序流，如果其本身是无序的，则返回其本身
    S unordered();

    //返回一个带着关闭处理的流，closeHandler线程将在调用close方法的时候调用
    S onClose(Runnable closeHandler);

    /**
     * Closes this stream, causing all close handlers for this stream pipeline
     * to be called.
     *
     * @see AutoCloseable#close()
     */
    //继承自于AutoCloseable接口，关流方法
    @Override
    void close();
}
