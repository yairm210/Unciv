package com.unciv.models;

import java.util.LinkedHashMap;

public class Counter<K> extends LinkedHashMap<K,Integer> {

    public Integer get(Object key){ // don't return null if empty
        if(containsKey(key)) return super.get(key);
        else return 0;
    }

    public void add(K key, int value){
        if(!containsKey(key)) put(key,value);
        else put(key,get(key)+value);
        if(get(key)==0) remove(key); // No objects of this sort left, no need to count
    }

    public void add(Counter<K> other){
        for (K key : other.keySet()) {
            add(key,other.get(key));
        }
    }

    public void remove(Counter<K> other){
        for (K key : other.keySet()) {
            add(key,-other.get(key));
        }
    }

    @Override
    public Counter<K> clone() {
        Counter<K> newCounter = new Counter<K>();
        newCounter.add(this);
        return newCounter;
    }
}
