package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.Index;

import java.io.File;
import java.util.List;

/**
 * Construct Lucene indexes
 */
public abstract class LuceneBuilder extends AbstractBuilder implements IndexBuilder<String, List<String>>
{
    public LuceneBuilder(File dataDirectory)
    {
        super(dataDirectory);
    }

    @Override
    public Index<String, List<String>> getIndex()
    {
        return null;
    }
}
