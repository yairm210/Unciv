package com.unciv.models.linq;

import java.util.LinkedHashMap;

public class LinqHashMap <K,V> extends LinkedHashMap<K,V> {
    public Linq<V> linqValues() {
        return new Linq<V>(super.values());
    }
}

