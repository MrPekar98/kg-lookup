package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.Index;
import dk.aau.dkw.kgservice.index.LuceneIndex;
import org.apache.jena.atlas.lib.Pair;
import org.apache.lucene.store.Directory;

import java.io.File;
import java.util.List;

/**
 * Construct Lucene indexes
 */
public abstract class LuceneBuilder implements IndexBuilder<String, List<Pair<String, Double>>>
{
    protected abstract Directory getDirectory();

    @Override
    public Index<String, List<Pair<String, Double>>> getIndex()
    {
        return new LuceneIndex(getDirectory());
    }

    @Override
    public Index<String, List<Pair<String, Double>>> build()
    {
        return abstractBuild();
    }

    protected abstract Index<String, List<Pair<String, Double>>> abstractBuild();
}
