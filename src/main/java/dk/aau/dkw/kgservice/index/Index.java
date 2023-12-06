package dk.aau.dkw.kgservice.index;

public interface Index<K, V>
{
    V get(K key);

    class LuceneIndex {
    }
}
