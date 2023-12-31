package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.Index;
import dk.aau.dkw.kgservice.index.LuceneIndex;
import dk.aau.dkw.kgservice.index.TDBIndex;
import dk.aau.dkw.kgservice.result.Result;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class LuceneTDBBuilder extends LuceneBuilder
{
    private TDBIndex tdb;
    private File luceneDir;
    private boolean closed = false;

    public LuceneTDBBuilder(File tdbDir, File luceneDir)
    {
        this.tdb = new TDBIndex(tdbDir);
        this.luceneDir = luceneDir;
    }

    @Override
    protected Index<String, List<Result>> abstractBuild()
    {
        if (this.closed)
        {
            throw new IllegalStateException("Lucene has already been constructed");
        }

        try (Analyzer analyzer = new StandardAnalyzer(); Directory dir = FSDirectory.open(this.luceneDir.toPath()))
        {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter writer = new IndexWriter(dir, config);
            this.tdb.forEach(key -> {
                try
                {
                    String entityUri = key.entity();

                    if (entityUri == null)
                    {
                        return;
                    }

                    TDBIndex.Query labelQuery = new TDBIndex.Query(entityUri, "http://www.w3.org/2000/01/rdf-schema#label"),
                            commentQuery = new TDBIndex.Query(entityUri, "http://www.w3.org/2000/01/rdf-schema#comment"),
                            categoryQuery = new TDBIndex.Query(entityUri, "http://dbpedia.org/ontology/category"),
                            descriptionQuery = new TDBIndex.Query(entityUri, "http://schema.org/description");
                    Set<String> labels = this.tdb.get(labelQuery),
                            comments = this.tdb.get(commentQuery),
                            categories = this.tdb.get(categoryQuery),
                            descriptions = this.tdb.get(descriptionQuery);

                    Document doc = new Document();
                    doc.add(new Field(LuceneIndex.URI_FIELD, entityUri, TextField.TYPE_STORED));
                    doc.add(new Field(LuceneIndex.COMMENT_FIELD, concat(comments), TextField.TYPE_STORED));
                    doc.add(new Field(LuceneIndex.CATEGORY_FIELD, concat(categories), TextField.TYPE_STORED));

                    if (labels.isEmpty())
                    {
                        String[] split = entityUri.split("/");
                        String label = split[split.length - 1].replace('_', ' ');
                        doc.add(new Field(LuceneIndex.LABEL_FIELD, label, TextField.TYPE_STORED));
                    }

                    else
                    {
                        doc.add(new Field(LuceneIndex.LABEL_FIELD, concat(labels), TextField.TYPE_STORED));
                    }

                    if (descriptions.isEmpty())
                    {
                        doc.add(new Field(LuceneIndex.DESCRIPTION_FIELD, concat(comments), TextField.TYPE_STORED));
                    }

                    else
                    {
                        doc.add(new Field(LuceneIndex.DESCRIPTION_FIELD, concat(descriptions), TextField.TYPE_STORED));
                    }

                    writer.addDocument(doc);
                }

                catch (IOException ignored) {}
            });

            this.closed = true;
            this.tdb.close();
            writer.close();

            return new LuceneIndex(dir);
        }

        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static String concat(Set<String> strings)
    {
        if (strings.isEmpty())
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (String str : strings)
        {
            builder.append(str).append(" ");
        }

        return builder.deleteCharAt(builder.length() - 1).toString();
    }

    @Override
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
}
