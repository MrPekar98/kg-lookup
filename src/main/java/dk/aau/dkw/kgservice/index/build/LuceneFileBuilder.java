package dk.aau.dkw.kgservice.index.build;

import dk.aau.dkw.kgservice.index.Index;
import dk.aau.dkw.kgservice.index.LuceneIndex;
import dk.aau.dkw.kgservice.result.Result;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class LuceneFileBuilder extends LuceneBuilder
{
    private final boolean logProgress;
    private File kgDir;
    private boolean isClosed = false;
    private String domain = null;
    private static final int LOG_BATCH = 100000;

    public LuceneFileBuilder(File luceneDir, File kgDir, String domain, boolean logProgress)
    {
        super(luceneDir);
        this.logProgress = logProgress;
        this.kgDir = kgDir;
        this.domain = domain;
    }

    public LuceneFileBuilder(File luceneDir, File kgDir, boolean logProgress)
    {
        this(luceneDir, kgDir, null, logProgress);
    }

    @Override
    protected Index<String, List<Result>> abstractBuild()
    {
        if (this.isClosed)
        {
            throw new IllegalStateException("Lucene has already been constructed");
        }

        else if (this.logProgress)
        {
            System.out.println("Collecting KG entities...");
        }

        long retrievals = 0, startTime = System.currentTimeMillis();
        Map<String, Map<String, Set<String>>> entities = new HashMap<>();
        Set<String> predicates = Set.of("http://www.w3.org/2000/01/rdf-schema#label",
                "http://www.w3.org/2000/01/rdf-schema#comment", "http://schema.org/description");

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

                        else if (retrievals++ % LOG_BATCH == 0 && this.logProgress)
                        {
                            long elapsedTime = System.currentTimeMillis() - startTime;
                            log(retrievals, elapsedTime, retrievals / LOG_BATCH);
                        }

                        String[] split = line.split(" ");
                        String entityUri = split[0].replace("<", "").replace(">", "");

                        if (entityUri.contains("Category:") || entityUri.contains("/prop") ||
                                (this.domain != null && !entityUri.contains(this.domain)))
                        {
                            super.skippedEntities.put(entityUri, "Does not belong to domain or contains \"Category:\" or \"/prop\".");
                            continue;
                        }

                        String predicate = split[1].replace("<", "").replace(">", "");

                        if (!predicates.contains(predicate))
                        {
                            continue;
                        }

                        else if (!entities.containsKey(entityUri))
                        {
                            entities.put(entityUri, new HashMap<>());

                            for (String pred : predicates)
                            {
                                entities.get(entityUri).put(pred, new HashSet<>());
                            }
                        }

                        int valueStart = line.lastIndexOf('>') + 2;
                        String value = line.substring(valueStart, line.length() - 2);

                        if (value.contains("@"))
                        {
                            if (!value.contains("@en"))
                            {
                                continue;
                            }

                            value = value.substring(0, value.indexOf("@")).replace("\"", "");
                        }

                        String[] tokens = value.split(" ");
                        entities.get(entityUri).get(predicate).addAll(List.of(tokens));
                    }
                }
            }

            if (this.logProgress)
            {
                System.out.println("\nWriting entities into Lucene...");
            }

            buildDocuments(entities, writer);
            System.out.println();
            this.isClosed = true;
            writer.close();

            return new LuceneIndex(dir, true);
        }

        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void buildDocuments(Map<String, Map<String, Set<String>>> entities, IndexWriter writer) throws IOException
    {
        int insertions = 0;
        long startTime = System.currentTimeMillis();

        for (Map.Entry<String, Map<String, Set<String>>> entry : entities.entrySet())
        {
            String uri = entry.getKey();
            Set<String> label = entry.getValue().get("http://www.w3.org/2000/01/rdf-schema#label"),
                    comment = entry.getValue().get("http://www.w3.org/2000/01/rdf-schema#comment"),
                    description = entry.getValue().get("http://schema.org/description");
            buildDocument(writer, uri, tokens2Str(label), tokens2Str(comment), tokens2Str(description));

            if (insertions++ % LOG_BATCH == 0 && this.logProgress)
            {
                long elapsed = System.currentTimeMillis() - startTime;
                log(insertions, elapsed, insertions);
            }
        }
    }

    private static String tokens2Str(Set<String> tokens)
    {
        StringBuilder builder = new StringBuilder();
        tokens.forEach(str -> builder.append(str).append(" "));
        return builder.toString();
    }
}
