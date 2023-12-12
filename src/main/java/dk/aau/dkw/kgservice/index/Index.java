package dk.aau.dkw.kgservice.index;

import java.util.Iterator;
import java.util.function.Consumer;

public interface Index<K, V>
{
    V get(K key);
    Iterator<K> keys();
    void forEach(Consumer<K> consumer);
}
