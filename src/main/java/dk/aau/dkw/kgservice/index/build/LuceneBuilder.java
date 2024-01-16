package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.Index;
import dk.aau.dkw.kgservice.index.LuceneIndex;
import dk.aau.dkw.kgservice.result.Result;
import org.apache.lucene.store.Directory;

import java.util.List;

/**
 * Construct Lucene indexes
 */
public abstract class LuceneBuilder implements IndexBuilder<String, List<Result>>
{
    protected abstract Directory getDirectory();

    @Override
    public Index<String, List<Result>> getIndex()
    {
        return new LuceneIndex(getDirectory());
    }

    @Override
    public Index<String, List<Result>> build()
    {
        return abstractBuild();
    }

    protected abstract Index<String, List<Result>> abstractBuild();
}
