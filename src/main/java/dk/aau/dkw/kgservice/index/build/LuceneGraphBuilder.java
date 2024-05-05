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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LuceneGraphBuilder extends LuceneBuilder
{
    private GraphIndex graph;
    private File luceneDir, kgDir;
    private String domain = null;
    private boolean closed = false;
    private final int BATCH_SIZE = 10;
    private final Set<String> existence = new HashSet<>();
    private final Map<String, String> skippedEntities = new HashMap<>();
    private final AtomicInteger insertedEntities = new AtomicInteger(0);
    private final boolean logProgress;

    public LuceneGraphBuilder(GraphIndex graph, File kgDir, File luceneDir, boolean logProgess)
    {
        this(graph, kgDir, luceneDir, null, logProgess);
    }

    public LuceneGraphBuilder(GraphIndex graph, File kgDir, File luceneDir, String domain, boolean logProgress)
    {
        this.graph = graph;
        this.kgDir = kgDir;
        this.luceneDir = luceneDir;
        this.domain = domain;
        this.logProgress = logProgress;
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
            int entityCount = 0;
            long timer = System.currentTimeMillis();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter writer = new IndexWriter(dir, config);
            final Map<String, String> predicateLabels = Map.of("http://www.w3.org/2000/01/rdf-schema#comment", "comment",
                    "http://schema.org/description", "description");

            for (File kgFile : Objects.requireNonNull(this.kgDir.listFiles()))
            {
                try (BufferedReader reader = Files.newBufferedReader(kgFile.toPath(), StandardCharsets.ISO_8859_1))
                {
                    String line;
                    Set<String> uris = new HashSet<>(BATCH_SIZE);
                    int insertCount = 0;
                    while ((line = reader.readLine()) != null)
                    {
                        if (line.startsWith("#"))
                        {
                            continue;
                        }

                        else if (insertCount >= BATCH_SIZE)
                        {
                            Set<Map<String, String>> results = this.graph.batchGet(uris, predicateLabels);
                            buildDocuments(writer, results);
                            entityCount += insertCount;
                            insertCount = 0;
                            uris.clear();

                            if (entityCount % 1000 == 0)
                            {
                                log(entityCount, System.currentTimeMillis() - timer, entityCount / BATCH_SIZE);
                            }
                        }

                        String[] split = line.split(" ");
                        String entityUri = split[0].replace("<", "").replace(">", "");

                        if (entityUri.contains("Category:") || entityUri.contains("/prop") ||
                                (this.domain != null && !entityUri.contains(this.domain)))
                        {
                            this.skippedEntities.put(entityUri, "Does not belong to domain or contains \"Category:\" or \"/prop\".");
                            continue;
                        }

                        String[] tokens = entityUri.split("/");
                        String postfix = tokens[tokens.length - 1];

                        if (this.existence.contains(postfix))
                        {
                            continue;
                        }

                        uris.add(entityUri);
                        insertCount++;
                    }

                    Set<Map<String, String>> results = this.graph.batchGet(uris, predicateLabels);
                    buildDocuments(writer, results);
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

    private void buildDocuments(IndexWriter writer, Set<Map<String, String>> results) throws IOException
    {
        for (Map<String, String> result : results)
        {
            String uri = result.get("uri");
            String label = result.getOrDefault("label", "");
            String comment = result.getOrDefault("comment", "");
            String description = result.getOrDefault("description", "");
            buildDocument(writer, uri, label, comment, description);
        }
    }

    private void buildDocument(IndexWriter writer, String uri, String label, String comment, String description) throws IOException
    {
        String[] uriTokens = uri.split("/");
        String uriPostfix = uriTokens[uriTokens.length - 1];
        Document doc = new Document();
        doc.add(new Field(LuceneIndex.URI_FIELD, uri, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.COMMENT_FIELD, comment, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.POSTFIX_FIELD, uriPostfix, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.LABEL_FIELD, label == null ? uriPostfix.replace('_', ' ') : label, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.DESCRIPTION_FIELD, description, TextField.TYPE_STORED));

        writer.addDocument(doc);
        this.insertedEntities.incrementAndGet();
        this.existence.add(uriPostfix);
    }

    private static void log(int insertedEntities, long totalElapsedTime, int insertions)
    {
        for (int i = 0; i < 256; i++)
        {
            System.out.print(" ");
        }

        System.out.println("Inserted " + insertedEntities + " entities (avg batching time " + (totalElapsedTime / insertions) + " ms)\r");
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
