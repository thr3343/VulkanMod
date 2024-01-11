package net.vulkanmod.render.chunk.util;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Consumer;

public class StaticArray<T> implements Iterable<T> {
    final T[] queue;
    int position = 0;
    int limit = 0;
    final int capacity;

    public StaticArray() {
        this(1024);
    }

    @SuppressWarnings("unchecked")
    public StaticArray(int initialCapacity) {
        this.capacity = initialCapacity;

        this.queue = (T[])(new Object[capacity]);
    }

    public boolean hasNext() {
        return this.position < this.limit;
    }

    public T poll() {
        T t = this.queue[position];
        this.position++;

        return t;
    }

    public void add(T t) {
        if(t == null)
            return;

        if(limit == capacity) throw new RuntimeException("Exceeded size: "+this.capacity);
        this.queue[limit] = t;

        this.limit++;
    }

    public T get(int a) {

        return this.queue[a];
    }

    public void put(int a, T b) {

        this.queue[a]=b;
    }

    public int size() {
        return limit;
    }

    public void clear() {
        this.position = 0;
        this.limit = 0;
    }

    public Iterator<T> iterator(boolean reverseOrder) {
        return reverseOrder ? new Iterator<>() {
            int pos = StaticArray.this.limit - 1;
            final int limit = -1;

            @Override
            public boolean hasNext() {
                return pos > limit;
            }

            @Override
            public T next() {
                return queue[pos--];
            }
        }
                : new Iterator<>() {
            int pos = 0;
            final int limit = StaticArray.this.limit;

            @Override
            public boolean hasNext() {
                return pos < limit;
            }

            @Override
            public T next() {
                return queue[pos++];
            }
        };
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return iterator(false);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for(int i = 0; i < this.limit; ++i) {
            action.accept(this.queue[i]);
        }

    }
}
