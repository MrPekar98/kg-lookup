package anonymous.kgservice.index.build;

import anonymous.kgservice.index.Index;

public interface IndexBuilder<K, V>
{
    Index<K, V> getIndex();
    Index<K, V> build();
}
