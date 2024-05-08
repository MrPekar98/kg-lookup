package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.Index;
import dk.aau.dkw.kgservice.index.LuceneIndex;
import dk.aau.dkw.kgservice.result.Result;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Construct Lucene indexes
 */
public abstract class LuceneBuilder implements IndexBuilder<String, List<Result>>
{
    protected File luceneDir;
    protected final Map<String, String> skippedEntities = new HashMap<>();
    protected final AtomicInteger insertedEntities = new AtomicInteger(0);

    protected LuceneBuilder(File luceneDir)
    {
        this.luceneDir = luceneDir;
    }

    protected static void log(long insertedEntities, long totalElapsedTime, long insertions)
    {
        for (int i = 0; i < 256; i++)
        {
            System.out.print(" ");
        }

        double avgBatchingTime = insertions == 0 ? totalElapsedTime : totalElapsedTime / insertions;

        System.out.print("\r");
        System.out.print("Inserted " + insertedEntities + " entities (avg batching time " + avgBatchingTime + " ms)\r");
    }

    protected Directory getDirectory()
    {
        try
        {
            return FSDirectory.open(this.luceneDir.toPath());
        }

        catch (IOException e)
        {
            return null;
        }
    }

    @Override
    public Index<String, List<Result>> getIndex()
    {
        return new LuceneIndex(getDirectory(), false);
    }

    @Override
    public Index<String, List<Result>> build()
    {
        return abstractBuild();
    }

    protected abstract Index<String, List<Result>> abstractBuild();

    protected void buildDocument(IndexWriter writer, String uri, String label, String comment, String description) throws IOException
    {
        String[] uriTokens = uri.split("/");
        String uriPostfix = uriTokens[uriTokens.length - 1].replace("_", " ");
        Document doc = new Document();
        doc.add(new Field(LuceneIndex.URI_FIELD, uri, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.COMMENT_FIELD, comment, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.POSTFIX_FIELD, uriPostfix, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.LABEL_FIELD, label == null ? uriPostfix.replace('_', ' ') : label, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.DESCRIPTION_FIELD, description, TextField.TYPE_STORED));

        writer.addDocument(doc);
        this.insertedEntities.incrementAndGet();
    }

    public Map<String, String> skippedEntities()
    {
        return this.skippedEntities;
    }

    public int insertedEntities()
    {
        return this.insertedEntities.get();
    }
}
