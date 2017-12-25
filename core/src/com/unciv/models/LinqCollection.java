package com.unciv.models;

import com.badlogic.gdx.utils.Predicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Created by LENOVO on 10/20/2017.
 */

public class LinqCollection <T> extends ArrayList<T> {
    public LinqCollection() {
    }

    public LinqCollection(Collection<? extends T> objects) {
        addAll(objects);
    }

    public LinqCollection(T ... objects) {
        addAll(Arrays.asList(objects));
    }




    public LinqCollection<T> where(Predicate<T> p) {
        LinqCollection<T> newCollection = new LinqCollection<T>();
        for (T t : this) if (p.evaluate(t)) newCollection.add(t);
        return newCollection;
    }

    public T first(Predicate<T> p) {
        for (T t : this) if (p.evaluate(t)) return t;
        return null;
    }

    public boolean any(Predicate<T> p){ return first(p) != null;}

    public int count(Predicate<T> p) {
        return where(p).size();
    }

    public <T2> LinqCollection<T2> select(Func<T, T2> selector) {
        LinqCollection<T2> newCollection = new LinqCollection<T2>();
        for (T t : this) newCollection.add(selector.GetBy(t));
        return newCollection;
    }

    public <T2> LinqCollection<T2> selectMany(Func<T,Collection<? extends T2>> multiSelector){
        LinqCollection<T2> newCollection = new LinqCollection<T2>();
        for(T t:this) newCollection.addAll(multiSelector.GetBy(t));
        return newCollection;
    }

    public T getRandom(){
        if(size()==0) return null;
        return get((int) (Math.random() * (size())));
    }

    public LinqCollection<T> unique() {
        return new LinqCollection<T>(new HashSet<T>(this)); // Shove it all into a hashset and build a new one around the results.
    }

    public interface Func<T1, T2> {
        public T2 GetBy(T1 arg0);
    }

    public <T2> LinqCollection<T2> as(Class<T2> t2Class){
        LinqCollection<T2> newCollection = new LinqCollection<T2>();
        for (T t:this) newCollection.add((T2)t);
        return newCollection;
    }


}


