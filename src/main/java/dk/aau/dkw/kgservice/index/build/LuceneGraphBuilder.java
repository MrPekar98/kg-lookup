package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.GraphIndex;
import dk.aau.dkw.kgservice.index.Index;
import dk.aau.dkw.kgservice.index.LuceneIndex;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class LuceneGraphBuilder extends LuceneBuilder
{
    private GraphIndex graph;
    private File luceneDir;
    private boolean closed = false;
    private final Map<String, String> skippedEntities = new HashMap<>();
    private final AtomicInteger insertedEntities = new AtomicInteger(0);

    public LuceneGraphBuilder(GraphIndex graph, File luceneDir)
    {
        this.graph = graph;
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
            this.graph.forEach(key -> {
                try
                {
                    String entityUri = key.entity();

                    if (entityUri == null || entityUri.contains("Category:") || entityUri.contains("/prop"))
                    {
                        return;
                    }

                    String[] tokens = entityUri.split("/");
                    String postfix = tokens[tokens.length - 1];
                    GraphIndex.Query labelQuery = new GraphIndex.Query(entityUri, "http://www.w3.org/2000/01/rdf-schema#label"),
                            commentQuery = new GraphIndex.Query(entityUri, "http://www.w3.org/2000/01/rdf-schema#comment"),
                            categoryQuery = new GraphIndex.Query(entityUri, "http://dbpedia.org/ontology/category"),
                            descriptionQuery = new GraphIndex.Query(entityUri, "http://schema.org/description");
                    Set<String> labels = this.graph.get(labelQuery),
                            comments = this.graph.get(commentQuery),
                            categories = this.graph.get(categoryQuery),
                            descriptions = this.graph.get(descriptionQuery);

                    Document doc = new Document();
                    doc.add(new Field(LuceneIndex.URI_FIELD, entityUri, TextField.TYPE_STORED));
                    doc.add(new Field(LuceneIndex.COMMENT_FIELD, concat(comments), TextField.TYPE_STORED));
                    doc.add(new Field(LuceneIndex.CATEGORY_FIELD, concat(categories), TextField.TYPE_STORED));
                    doc.add(new Field(LuceneIndex.POSTFIX_FIELD, postfix, TextField.TYPE_STORED));

                    if (labels.isEmpty())
                    {
                        String label = postfix.replace('_', ' ');
                        doc.add(new Field(LuceneIndex.LABEL_FIELD, label, TextField.TYPE_STORED));
                    }

                    else
                    {
                        for (String label : labels)
                        {
                            if (!label.contains("?"))
                            {
                                doc.add(new Field(LuceneIndex.LABEL_FIELD, label, TextField.TYPE_STORED));
                                break;
                            }
                        }
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
                    this.insertedEntities.incrementAndGet();
                }

                catch (IOException e)
                {
                    this.skippedEntities.put(key.entity(), e.getMessage());
                }
            });

            this.closed = true;
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

    public Map<String, String> skippedEntities()
    {
        return this.skippedEntities;
    }

    public int insertedEntities()
    {
        return this.insertedEntities.get();
    }
}
