package dk.aau.dkw.kgservice.index;

import java.util.Iterator;

public interface Index<K, V>
{
    V get(K key);
    Iterator<K> keys();
}
