package com.unciv.models;

import java.util.LinkedHashMap;

public class LinqCounter<K> extends LinkedHashMap<K,Integer> {

    public Integer get(Object key){ // don't return null if empty
        if(containsKey(key)) return super.get(key);
        else return 0;
    }

    public void add(K key, int value){
        if(!containsKey(key)) put(key,value);
        else put(key,get(key)+value);
        if(get(key)==0) remove(key); // No objects of this sort left, no need to count
    }

    public void add(LinqCounter<K> other){
        for (K key : other.keySet()) {
            add(key,other.get(key));
        }
    }
}
