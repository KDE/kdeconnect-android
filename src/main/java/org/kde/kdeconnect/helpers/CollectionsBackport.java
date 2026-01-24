/*
 * SPDX-FileCopyrightText: 2014 The Android Open Source Project
 * SPDX-FileCopyrightText: 1997, 2021, Oracle and/or its affiliates. All rights reserved
 *
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers;

import android.os.Build;
import android.os.Build.VERSION;

import androidx.annotation.RequiresApi;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** @noinspection unused*/
public final class CollectionsBackport {
    public static <T> NavigableSet<T> unmodifiableNavigableSet(NavigableSet<T> s) {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Collections.unmodifiableNavigableSet(s);
        } else {
            return new UnmodifiableNavigableSetBackport<>(s);
        }
    }

    public static <T> Set<T> unmodifiableSet(Set<T> s) {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Collections.unmodifiableSet(s);
        } else {
            return new UnmodifiableSetBackport<>(s);
        }
    }

    public static <T> Collection<T> unmodifiableCollection(Collection<T> c) {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Collections.unmodifiableCollection(c);
        } else {
            return new UnmodifiableCollectionBackport<>(c);
        }
    }

    public static <K, V> NavigableMap<K, V> unmodifiableNavigableMap(NavigableMap<K, V> m) {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Collections.unmodifiableNavigableMap(m);
        } else {
            return new UnmodifiableNavigableMapBackport<>(m);
        }
    }

    public static <K, V> Map<K, V> unmodifiableMap(Map<K, V> m) {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Collections.unmodifiableMap(m);
        } else {
            return new UnmodifiableMapBackport<>(m);
        }
    }

    public static <T> NavigableSet<T> emptyNavigableSet() {
        //noinspection unchecked
        return (NavigableSet<T>) UnmodifiableNavigableSetBackport.EMPTY_NAVIGABLE_SET;
    }

    public static <K, V> NavigableMap<K, V> emptyNavigableMap() {
        //noinspection unchecked
        return (NavigableMap<K, V>) UnmodifiableNavigableMapBackport.EMPTY_NAVIGABLE_MAP;
    }

    static boolean eq(Object o1, Object o2) {
        return Objects.equals(o1, o2);
    }

    static class UnmodifiableNavigableSetBackport<E>
            extends UnmodifiableSortedSetBackport<E>
            implements NavigableSet<E>, Serializable {

        /**
         * A singleton empty unmodifiable navigable set used for
         * {@link #emptyNavigableSet()}.
         *
         * @param <E> type of elements, if there were any, and bounds
         */
        private static class EmptyNavigableSet<E> extends UnmodifiableNavigableSetBackport<E>
                implements Serializable {
            public EmptyNavigableSet() {
                super(new TreeSet<>());
            }

            @java.io.Serial
            private Object readResolve() {
                return EMPTY_NAVIGABLE_SET;
            }
        }

        @SuppressWarnings("rawtypes")
        private static final NavigableSet<?> EMPTY_NAVIGABLE_SET =
                new EmptyNavigableSet<>();

        /**
         * The instance we are protecting.
         */
        @SuppressWarnings("serial") // Conditionally serializable
        private final NavigableSet<E> ns;

        UnmodifiableNavigableSetBackport(NavigableSet<E> s) {
            super(s);
            ns = s;
        }

        public E lower(E e) {
            return ns.lower(e);
        }

        public E floor(E e) {
            return ns.floor(e);
        }

        public E ceiling(E e) {
            return ns.ceiling(e);
        }

        public E higher(E e) {
            return ns.higher(e);
        }

        public E pollFirst() {
            throw new UnsupportedOperationException();
        }

        public E pollLast() {
            throw new UnsupportedOperationException();
        }

        public NavigableSet<E> descendingSet() {
            return new UnmodifiableNavigableSetBackport<>(ns.descendingSet());
        }

        public Iterator<E> descendingIterator() {
            return descendingSet().iterator();
        }

        public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
            return new UnmodifiableNavigableSetBackport<>(
                    ns.subSet(fromElement, fromInclusive, toElement, toInclusive));
        }

        public NavigableSet<E> headSet(E toElement, boolean inclusive) {
            return new UnmodifiableNavigableSetBackport<>(
                    ns.headSet(toElement, inclusive));
        }

        public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
            return new UnmodifiableNavigableSetBackport<>(
                    ns.tailSet(fromElement, inclusive));
        }
    }

    static class UnmodifiableSortedSetBackport<E>
            extends UnmodifiableSetBackport<E>
            implements SortedSet<E>, Serializable {
        @SuppressWarnings("serial") // Conditionally serializable
        private final SortedSet<E> ss;

        UnmodifiableSortedSetBackport(SortedSet<E> s) {
            super(s);
            ss = s;
        }

        public Comparator<? super E> comparator() {
            return ss.comparator();
        }

        public SortedSet<E> subSet(E fromElement, E toElement) {
            return new UnmodifiableSortedSetBackport<>(ss.subSet(fromElement, toElement));
        }

        public SortedSet<E> headSet(E toElement) {
            return new UnmodifiableSortedSetBackport<>(ss.headSet(toElement));
        }

        public SortedSet<E> tailSet(E fromElement) {
            return new UnmodifiableSortedSetBackport<>(ss.tailSet(fromElement));
        }

        public E first() {
            return ss.first();
        }

        public E last() {
            return ss.last();
        }
    }

    static class UnmodifiableSetBackport<E> extends UnmodifiableCollectionBackport<E>
            implements Set<E>, Serializable {

        UnmodifiableSetBackport(Set<? extends E> s) {
            super(s);
        }

        public boolean equals(Object o) {
            return o == this || c.equals(o);
        }

        public int hashCode() {
            return c.hashCode();
        }
    }

    static class UnmodifiableCollectionBackport<E> implements Collection<E>, Serializable {

        @SuppressWarnings("serial") // Conditionally serializable
        final Collection<? extends E> c;

        UnmodifiableCollectionBackport(Collection<? extends E> c) {
            if (c == null)
                throw new NullPointerException();
            this.c = c;
        }

        public int size() {
            return c.size();
        }

        public boolean isEmpty() {
            return c.isEmpty();
        }

        public boolean contains(Object o) {
            return c.contains(o);
        }

        public Object[] toArray() {
            return c.toArray();
        }

        public <T> T[] toArray(T[] a) {
            return c.toArray(a);
        }

        @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
        public <T> T[] toArray(IntFunction<T[]> f) {
            return c.toArray(f);
        }

        public String toString() {
            return c.toString();
        }

        public Iterator<E> iterator() {
            return new Iterator<>() {
                private final Iterator<? extends E> i = c.iterator();

                public boolean hasNext() {
                    return i.hasNext();
                }

                public E next() {
                    return i.next();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void forEachRemaining(Consumer<? super E> action) {
                    // Use backing collection version
                    i.forEachRemaining(action);
                }
            };
        }

        public boolean add(E e) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        public boolean containsAll(Collection<?> coll) {
            return c.containsAll(coll);
        }

        public boolean addAll(Collection<? extends E> coll) {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> coll) {
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> coll) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }

        // Override default methods in Collection
        @Override
        public void forEach(Consumer<? super E> action) {
            c.forEach(action);
        }

        @Override
        public boolean removeIf(Predicate<? super E> filter) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Spliterator<E> spliterator() {
            return (Spliterator<E>) c.spliterator();
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @SuppressWarnings("unchecked")
        @Override
        public Stream<E> stream() {
            return (Stream<E>) c.stream();
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @SuppressWarnings("unchecked")
        @Override
        public Stream<E> parallelStream() {
            return (Stream<E>) c.parallelStream();
        }
    }

    static class UnmodifiableNavigableMapBackport<K, V> extends UnmodifiableSortedMapBackport<K, V> implements NavigableMap<K, V>, Serializable {
        private static final long serialVersionUID = -4858195264774772197L;
        private static final EmptyNavigableMapBackport<?, ?> EMPTY_NAVIGABLE_MAP = new EmptyNavigableMapBackport();
        private final NavigableMap<K, ? extends V> nm;

        UnmodifiableNavigableMapBackport(NavigableMap<K, ? extends V> m) {
            super(m);
            this.nm = m;
        }

        public K lowerKey(K key) {
            return this.nm.lowerKey(key);
        }

        public K floorKey(K key) {
            return this.nm.floorKey(key);
        }

        public K ceilingKey(K key) {
            return this.nm.ceilingKey(key);
        }

        public K higherKey(K key) {
            return this.nm.higherKey(key);
        }

        public Map.Entry<K, V> lowerEntry(K key) {
            Map.Entry<K, V> lower = (Entry<K, V>) this.nm.lowerEntry(key);
            return null != lower ? new UnmodifiableMapBackport.UnmodifiableEntrySetBackport.UnmodifiableEntry(lower) : null;
        }

        public Map.Entry<K, V> floorEntry(K key) {
            Map.Entry<K, V> floor = (Entry<K, V>) this.nm.floorEntry(key);
            return null != floor ? new UnmodifiableMapBackport.UnmodifiableEntrySetBackport.UnmodifiableEntry(floor) : null;
        }

        public Map.Entry<K, V> ceilingEntry(K key) {
            Map.Entry<K, V> ceiling = (Entry<K, V>) this.nm.ceilingEntry(key);
            return null != ceiling ? new UnmodifiableMapBackport.UnmodifiableEntrySetBackport.UnmodifiableEntry(ceiling) : null;
        }

        public Map.Entry<K, V> higherEntry(K key) {
            Map.Entry<K, V> higher = (Entry<K, V>) this.nm.higherEntry(key);
            return null != higher ? new UnmodifiableMapBackport.UnmodifiableEntrySetBackport.UnmodifiableEntry(higher) : null;
        }

        public Map.Entry<K, V> firstEntry() {
            Map.Entry<K, V> first = (Entry<K, V>) this.nm.firstEntry();
            return null != first ? new UnmodifiableMapBackport.UnmodifiableEntrySetBackport.UnmodifiableEntry(first) : null;
        }

        public Map.Entry<K, V> lastEntry() {
            Map.Entry<K, V> last = (Entry<K, V>) this.nm.lastEntry();
            return null != last ? new UnmodifiableMapBackport.UnmodifiableEntrySetBackport.UnmodifiableEntry(last) : null;
        }

        public Map.Entry<K, V> pollFirstEntry() {
            throw new UnsupportedOperationException();
        }

        public Map.Entry<K, V> pollLastEntry() {
            throw new UnsupportedOperationException();
        }

        public NavigableMap<K, V> descendingMap() {
            return (NavigableMap<K, V>) CollectionsBackport.unmodifiableNavigableMap(this.nm.descendingMap());
        }

        public NavigableSet<K> navigableKeySet() {
            return CollectionsBackport.unmodifiableNavigableSet(this.nm.navigableKeySet());
        }

        public NavigableSet<K> descendingKeySet() {
            return CollectionsBackport.unmodifiableNavigableSet(this.nm.descendingKeySet());
        }

        public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
            return (NavigableMap<K, V>) CollectionsBackport.unmodifiableNavigableMap(this.nm.subMap(fromKey, fromInclusive, toKey, toInclusive));
        }

        public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
            return (NavigableMap<K, V>) CollectionsBackport.unmodifiableNavigableMap(this.nm.headMap(toKey, inclusive));
        }

        public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
            return (NavigableMap<K, V>) CollectionsBackport.unmodifiableNavigableMap(this.nm.tailMap(fromKey, inclusive));
        }

        private static class EmptyNavigableMapBackport<K, V> extends UnmodifiableNavigableMapBackport<K, V> implements Serializable {
            private static final long serialVersionUID = -2239321462712562324L;

            EmptyNavigableMapBackport() {
                super(new TreeMap());
            }

            public NavigableSet<K> navigableKeySet() {
                return CollectionsBackport.emptyNavigableSet();
            }

            private Object readResolve() {
                return UnmodifiableNavigableMapBackport.EMPTY_NAVIGABLE_MAP;
            }
        }
    }

    static class UnmodifiableSortedMapBackport<K,V>
            extends UnmodifiableMapBackport<K,V>
            implements SortedMap<K,V>, Serializable {
        @SuppressWarnings("serial") // Conditionally serializable
        private final SortedMap<K, ? extends V> sm;

        UnmodifiableSortedMapBackport(SortedMap<K, ? extends V> m) {super(m); sm = m; }
        public Comparator<? super K> comparator()   { return sm.comparator(); }
        public SortedMap<K,V> subMap(K fromKey, K toKey)
        { return new UnmodifiableSortedMapBackport<>(sm.subMap(fromKey, toKey)); }
        public SortedMap<K,V> headMap(K toKey)
        { return new UnmodifiableSortedMapBackport<>(sm.headMap(toKey)); }
        public SortedMap<K,V> tailMap(K fromKey)
        { return new UnmodifiableSortedMapBackport<>(sm.tailMap(fromKey)); }
        public K firstKey()                           { return sm.firstKey(); }
        public K lastKey()                             { return sm.lastKey(); }
    }

    private static class UnmodifiableMapBackport<K, V> implements Map<K, V>, Serializable {
        @java.io.Serial
        private static final long serialVersionUID = -1034234728574286014L;

        @SuppressWarnings("serial") // Conditionally serializable
        private final Map<? extends K, ? extends V> m;

        UnmodifiableMapBackport(Map<? extends K, ? extends V> m) {
            if (m == null)
                throw new NullPointerException();
            this.m = m;
        }

        public int size() {
            return m.size();
        }

        public boolean isEmpty() {
            return m.isEmpty();
        }

        public boolean containsKey(Object key) {
            return m.containsKey(key);
        }

        public boolean containsValue(Object val) {
            return m.containsValue(val);
        }

        public V get(Object key) {
            return m.get(key);
        }

        public V put(K key, V value) {
            throw new UnsupportedOperationException();
        }

        public V remove(Object key) {
            throw new UnsupportedOperationException();
        }

        public void putAll(Map<? extends K, ? extends V> m) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }

        private transient Set<K> keySet;
        private transient Set<Map.Entry<K, V>> entrySet;
        private transient Collection<V> values;

        public Set<K> keySet() {
            if (keySet == null)
                keySet = (Set<K>) unmodifiableSet(m.keySet());
            return keySet;
        }

        public Set<Map.Entry<K, V>> entrySet() {
            if (entrySet == null)
                entrySet = new UnmodifiableEntrySetBackport<>(m.entrySet());
            return entrySet;
        }

        public Collection<V> values() {
            if (values == null)
                values = (Collection<V>) unmodifiableCollection(m.values());
            return values;
        }

        public boolean equals(Object o) {
            return o == this || m.equals(o);
        }

        public int hashCode() {
            return m.hashCode();
        }

        public String toString() {
            return m.toString();
        }

        // Override default methods in Map
        @Override
        @SuppressWarnings("unchecked")
        public V getOrDefault(Object k, V defaultValue) {
            // Safe cast as we don't change the value
            return ((Map<K, V>) m).getOrDefault(k, defaultValue);
        }

        @Override
        public void forEach(BiConsumer<? super K, ? super V> action) {
            m.forEach(action);
        }

        @Override
        public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V putIfAbsent(K key, V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object key, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean replace(K key, V oldValue, V newValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V replace(K key, V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V computeIfPresent(K key,
                                  BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V compute(K key,
                         BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V merge(K key, V value,
                       BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
            throw new UnsupportedOperationException();
        }

        /**
         * We need this class in addition to UnmodifiableSet as
         * Map.Entries themselves permit modification of the backing Map
         * via their setValue operation.  This class is subtle: there are
         * many possible attacks that must be thwarted.
         *
         * @serial include
         */
        static class UnmodifiableEntrySetBackport<K, V>
                extends UnmodifiableSetBackport<Map.Entry<K, V>> {
            @java.io.Serial
            private static final long serialVersionUID = 7854390611657943733L;

            @SuppressWarnings({"unchecked", "rawtypes"})
            UnmodifiableEntrySetBackport(Set<? extends Map.Entry<? extends K, ? extends V>> s) {
                // Need to cast to raw in order to work around a limitation in the type system
                super((Set) s);
            }

            static <K, V> Consumer<Map.Entry<? extends K, ? extends V>> entryConsumer(
                    Consumer<? super Entry<K, V>> action) {
                return e -> action.accept(new UnmodifiableEntry<>(e));
            }

            public void forEach(Consumer<? super Entry<K, V>> action) {
                Objects.requireNonNull(action);
                c.forEach(entryConsumer(action));
            }

            static final class UnmodifiableEntrySetSpliterator<K, V>
                    implements Spliterator<Entry<K, V>> {
                final Spliterator<Map.Entry<K, V>> s;

                UnmodifiableEntrySetSpliterator(Spliterator<Entry<K, V>> s) {
                    this.s = s;
                }

                @Override
                public boolean tryAdvance(Consumer<? super Entry<K, V>> action) {
                    Objects.requireNonNull(action);
                    return s.tryAdvance(entryConsumer(action));
                }

                @Override
                public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
                    Objects.requireNonNull(action);
                    s.forEachRemaining(entryConsumer(action));
                }

                @Override
                public Spliterator<Entry<K, V>> trySplit() {
                    Spliterator<Entry<K, V>> split = s.trySplit();
                    return split == null
                            ? null
                            : new UnmodifiableEntrySetSpliterator<>(split);
                }

                @Override
                public long estimateSize() {
                    return s.estimateSize();
                }

                @Override
                public long getExactSizeIfKnown() {
                    return s.getExactSizeIfKnown();
                }

                @Override
                public int characteristics() {
                    return s.characteristics();
                }

                @Override
                public boolean hasCharacteristics(int characteristics) {
                    return s.hasCharacteristics(characteristics);
                }

                @Override
                public Comparator<? super Entry<K, V>> getComparator() {
                    return s.getComparator();
                }
            }

            @SuppressWarnings("unchecked")
            public Spliterator<Entry<K, V>> spliterator() {
                return new UnmodifiableEntrySetSpliterator<>(
                        (Spliterator<Map.Entry<K, V>>) c.spliterator());
            }

            @Override
            public Stream<Entry<K, V>> stream() {
                return StreamSupport.stream(spliterator(), false);
            }

            @Override
            public Stream<Entry<K, V>> parallelStream() {
                return StreamSupport.stream(spliterator(), true);
            }

            public Iterator<Map.Entry<K, V>> iterator() {
                return new Iterator<>() {
                    private final Iterator<? extends Map.Entry<? extends K, ? extends V>> i = c.iterator();

                    public boolean hasNext() {
                        return i.hasNext();
                    }

                    public Map.Entry<K, V> next() {
                        return new UnmodifiableEntry<>(i.next());
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    // Seems like an oversight. http://b/110351017
                    public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
                        i.forEachRemaining(entryConsumer(action));
                    }
                };
            }

            @SuppressWarnings("unchecked")
            public Object[] toArray() {
                Object[] a = c.toArray();
                for (int i = 0; i < a.length; i++)
                    a[i] = new UnmodifiableEntry<>((Map.Entry<? extends K, ? extends V>) a[i]);
                return a;
            }

            @SuppressWarnings("unchecked")
            public <T> T[] toArray(T[] a) {
                // We don't pass a to c.toArray, to avoid window of
                // vulnerability wherein an unscrupulous multithreaded client
                // could get his hands on raw (unwrapped) Entries from c.
                Object[] arr = c.toArray(a.length == 0 ? a : Arrays.copyOf(a, 0));

                for (int i = 0; i < arr.length; i++)
                    arr[i] = new UnmodifiableEntry<>((Map.Entry<? extends K, ? extends V>) arr[i]);

                if (arr.length > a.length)
                    return (T[]) arr;

                System.arraycopy(arr, 0, a, 0, arr.length);
                if (a.length > arr.length)
                    a[arr.length] = null;
                return a;
            }

            /**
             * This method is overridden to protect the backing set against
             * an object with a nefarious equals function that senses
             * that the equality-candidate is Map.Entry and calls its
             * setValue method.
             */
            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                return c.contains(
                        new UnmodifiableEntry<>((Map.Entry<?, ?>) o));
            }

            /**
             * The next two methods are overridden to protect against
             * an unscrupulous List whose contains(Object o) method senses
             * when o is a Map.Entry, and calls o.setValue.
             */
            public boolean containsAll(Collection<?> coll) {
                for (Object e : coll) {
                    if (!contains(e)) // Invokes safe contains() above
                        return false;
                }
                return true;
            }

            public boolean equals(Object o) {
                if (o == this)
                    return true;

                // Android-changed: (b/247094511) instanceof pattern variable is not yet supported.
                /*
                return o instanceof Set<?> s
                        && s.size() == c.size()
                        && containsAll(s); // Invokes safe containsAll() above
                 */
                if (!(o instanceof Set))
                    return false;
                Set<?> s = (Set<?>) o;
                if (s.size() != c.size())
                    return false;
                return containsAll(s); // Invokes safe containsAll() above
            }

            /**
             * This "wrapper class" serves two purposes: it prevents
             * the client from modifying the backing Map, by short-circuiting
             * the setValue method, and it protects the backing Map against
             * an ill-behaved Map.Entry that attempts to modify another
             * Map Entry when asked to perform an equality check.
             */
            private static class UnmodifiableEntry<K, V> implements Map.Entry<K, V> {
                private Map.Entry<? extends K, ? extends V> e;

                UnmodifiableEntry(Map.Entry<? extends K, ? extends V> e) {
                    this.e = Objects.requireNonNull(e);
                }

                public K getKey() {
                    return e.getKey();
                }

                public V getValue() {
                    return e.getValue();
                }

                public V setValue(V value) {
                    throw new UnsupportedOperationException();
                }

                public int hashCode() {
                    return e.hashCode();
                }

                public boolean equals(Object o) {
                    if (this == o)
                        return true;
                    // Android-changed: (b/247094511) instanceof pattern variable is not yet
                    // supported.
                    /*
                    return o instanceof Map.Entry<?, ?> t
                            && eq(e.getKey(),   t.getKey())
                            && eq(e.getValue(), t.getValue());
                    */
                    if (!(o instanceof Map.Entry))
                        return false;
                    Map.Entry<?, ?> t = (Map.Entry<?, ?>) o;
                    return eq(e.getKey(), t.getKey()) &&
                            eq(e.getValue(), t.getValue());
                }

                public String toString() {
                    return e.toString();
                }
            }
        }
    }
}
