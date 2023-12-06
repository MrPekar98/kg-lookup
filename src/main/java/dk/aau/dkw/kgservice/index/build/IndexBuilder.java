package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.Index;

public interface IndexBuilder<K, V>
{
    Index<K, V> getIndex();
    Index<K, V> build();
}
