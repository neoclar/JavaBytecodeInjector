package com.neoclar.jbi.java.utils;

public class Pair<K,V>
{
    public K k;
    public V v;
    public Pair(K key, V value) {
        k=key;
        v=value;
    }
    public K getKey() {
        return k;
    }
    public V getValue() {
        return v;
    }
    @Override
    public String toString() {
        return "<"+k+" :=: "+v+">";
    }
}