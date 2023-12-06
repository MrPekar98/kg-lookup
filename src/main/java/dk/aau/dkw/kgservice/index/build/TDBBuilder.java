package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.Index;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.tdb.TDBFactory;

import java.io.File;
import java.util.Set;

/**
 * Construct Jena TDB indexes
 */
public class TDBBuilder extends AbstractBuilder implements IndexBuilder<String, Set<String>>
{
    public TDBBuilder(File dataDirectory)
    {
        super(dataDirectory);
        load();
    }

    private void load()
    {
        Dataset dataset = TDBFactory.createDataset(getDataDirectory().getAbsolutePath());
        dataset.begin(ReadWrite.WRITE);
        dataset.end();
    }

    @Override
    public Index<String, Set<String>> getIndex()
    {
        return null;
    }

    @Override
    public Index<String, Set<String>> build()
    {

    }
}
