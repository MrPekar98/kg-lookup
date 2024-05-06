package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.GraphIndex;
import dk.aau.dkw.kgservice.index.Index;
import dk.aau.dkw.kgservice.index.LuceneIndex;
import dk.aau.dkw.kgservice.result.Result;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class LuceneGraphBuilder extends LuceneBuilder
{
    private GraphIndex graph;
    private File kgDir;
    private String domain = null;
    private boolean closed = false;
    private final Set<String> existence = new HashSet<>();
    private final int BATCH_SIZE = 10;
    private final boolean logProgress;

    public LuceneGraphBuilder(GraphIndex graph, File kgDir, File luceneDir, boolean logProgess)
    {
        this(graph, kgDir, luceneDir, null, logProgess);
    }

    public LuceneGraphBuilder(GraphIndex graph, File kgDir, File luceneDir, String domain, boolean logProgress)
    {
        super(luceneDir);
        this.graph = graph;
        this.kgDir = kgDir;
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

                            if (entityCount % 1000 == 0 && this.logProgress)
                            {
                                log(entityCount, System.currentTimeMillis() - timer, entityCount / BATCH_SIZE);
                            }
                        }

                        String[] split = line.split(" ");
                        String entityUri = split[0].replace("<", "").replace(">", "");

                        if (entityUri.contains("Category:") || entityUri.contains("/prop") ||
                                (this.domain != null && !entityUri.contains(this.domain)))
                        {
                            super.skippedEntities.put(entityUri, "Does not belong to domain or contains \"Category:\" or \"/prop\".");
                            continue;
                        }

                        String[] tokens = entityUri.split("/");
                        String postfix = tokens[tokens.length - 1];

                        if (this.existence.contains(postfix))
                        {
                            continue;
                        }

                        uris.add(entityUri);
                        this.existence.add(postfix);
                        insertCount++;
                    }

                    Set<Map<String, String>> results = this.graph.batchGet(uris, predicateLabels);
                    buildDocuments(writer, results);
                }
            }

            this.closed = true;
            this.existence.clear();
            writer.close();

            return new LuceneIndex(dir, true);
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
}
