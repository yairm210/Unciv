package com.unciv.models;

import java.util.LinkedHashMap;

public class LinqHashMap <K,V> extends LinkedHashMap<K,V> {
    public LinqCollection<V> linqValues() {
        return new LinqCollection<V>(super.values());
    }
}
