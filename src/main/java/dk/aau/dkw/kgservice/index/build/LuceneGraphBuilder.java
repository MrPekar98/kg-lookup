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

import java.io.*;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LuceneGraphBuilder extends LuceneBuilder
{
    private GraphIndex graph;
    private File luceneDir, kgDir;
    private boolean closed = false;
    private final Set<String> existence = new HashSet<>();
    private final Map<String, String> skippedEntities = new HashMap<>();
    private final AtomicInteger insertedEntities = new AtomicInteger(0);

    public LuceneGraphBuilder(GraphIndex graph, File kgDir, File luceneDir)
    {
        this.graph = graph;
        this.kgDir = kgDir;
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

            for (File kgFile : Objects.requireNonNull(this.kgDir.listFiles()))
            {
                try (BufferedReader reader = Files.newBufferedReader(kgFile.toPath(), StandardCharsets.ISO_8859_1))
                {
                    String line;

                    while ((line = reader.readLine()) != null)
                    {
                        if (line.startsWith("#"))
                        {
                            continue;
                        }

                        String[] split = line.split(" ");
                        String entityUri = split[0].replace("<", "").replace(">", "");

                        if (entityUri.contains("Category:") || entityUri.contains("/prop"))
                        {
                            continue;
                        }

                        String[] tokens = entityUri.split("/");
                        String postfix = tokens[tokens.length - 1];

                        if (this.existence.contains(postfix))
                        {
                            continue;
                        }

                        GraphIndex.Query labelQuery = new GraphIndex.Query(entityUri, "http://www.w3.org/2000/01/rdf-schema#label"),
                                commentQuery = new GraphIndex.Query(entityUri, "http://www.w3.org/2000/01/rdf-schema#comment"),
                                categoryQuery = new GraphIndex.Query(entityUri, "http://dbpedia.org/ontology/category"),
                                descriptionQuery = new GraphIndex.Query(entityUri, "http://schema.org/description");
                        Set<String> labels = this.graph.get(labelQuery),    // TODO: This is inefficient and should be one query
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
                        this.existence.add(postfix);
                    }
                }
            }

            this.closed = true;
            writer.close();
            this.existence.clear();

            return new LuceneIndex(dir, false);
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
