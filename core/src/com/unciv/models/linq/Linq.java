package com.unciv.models.linq;

import com.badlogic.gdx.utils.Predicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Created by LENOVO on 10/20/2017.
 */

public class Linq<T> extends ArrayList<T> {
    public Linq() {
    }

    public Linq(Collection<? extends T> objects) {
        addAll(objects);
    }

    public Linq(T ... objects) {
        addAll(Arrays.asList(objects));
    }




    public Linq<T> where(Predicate<T> p) {
        Linq<T> newCollection = new Linq<T>();
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

    public <T2> Linq<T2> select(Func<T, T2> selector) {
        Linq<T2> newCollection = new Linq<T2>();
        for (T t : this) newCollection.add(selector.GetBy(t));
        return newCollection;
    }

    public <T2> Linq<T2> selectMany(Func<T,Collection<? extends T2>> multiSelector){
        Linq<T2> newCollection = new Linq<T2>();
        for(T t:this) newCollection.addAll(multiSelector.GetBy(t));
        return newCollection;
    }

    public T getRandom(){
        if(size()==0) return null;
        return get((int) (Math.random() * (size())));
    }

    public Linq<T> unique() {
        return new Linq<T>(new HashSet<T>(this)); // Shove it all into a hashset and build a new one around the results.
    }

    public interface Func<T1, T2> {
        public T2 GetBy(T1 arg0);
    }

    public <T2> Linq<T2> as(Class<T2> t2Class){
        Linq<T2> newCollection = new Linq<T2>();
        for (T t:this) newCollection.add((T2)t);
        return newCollection;
    }

    public Linq<T> reverse(){
        Linq<T> newCol = clone();
        Collections.reverse(newCol);
        return newCol;
    }

    public Linq<T> clone(){
        return new Linq<T>(this);
    }
}