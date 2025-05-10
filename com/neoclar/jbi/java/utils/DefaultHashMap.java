package com.neoclar.jbi.java.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

@SuppressWarnings("rawtypes")
public class DefaultHashMap<K,V> extends HashMap<K,V> {
  protected Class defaultValue;
  protected Class[] classArgs;
  protected Object[] args;
    public DefaultHashMap(Class defaultValue, Class[] classArgs, Object[] args) {
      this.defaultValue = defaultValue;
      if (classArgs==null) {this.classArgs = new Class[]{};}
      else {this.classArgs = classArgs;}
      if (args==null) {this.args = new Object[]{};}
      else {this.args = args;}
    }
    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
      if (!containsKey(key)) {
        Object newValue=null;
        try {newValue = defaultValue.getConstructor(classArgs).newInstance(args);}
        catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e)
        {e.printStackTrace();}
        put((K)key, (V)newValue);
      }
      return super.get(key);
    }
    public V getWithoutDefault(Object key) {
      return super.get(key);
    }
  }