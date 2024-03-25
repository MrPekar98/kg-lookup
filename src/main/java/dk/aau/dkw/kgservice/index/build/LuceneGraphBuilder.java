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
    private boolean closed = false;
    private final int BATCH_SIZE = 10;
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
            final Map<String, String> predicateLabels = Map.of("http://www.w3.org/2000/01/rdf-schema#label", "label",
                    "http://www.w3.org/2000/01/rdf-schema#comment", "comment",
                    "http://dbpedia.org/ontology/category", "category",
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
                            insertCount = 0;
                            uris.clear();
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

    private void buildDocuments(IndexWriter writer, Set<Map<String, String>> documents) throws IOException
    {
        for (Map<String, String> document : documents)
        {
            String uri = document.get("uri");
            String[] uriTokens = uri.split("/");
            String uriPostfix = uriTokens[uriTokens.length - 1];
            Document doc = new Document();
            doc.add(new Field(LuceneIndex.URI_FIELD, uri, TextField.TYPE_STORED));
            doc.add(new Field(LuceneIndex.POSTFIX_FIELD, uriPostfix, TextField.TYPE_STORED));
            doc.add(new Field(LuceneIndex.LABEL_FIELD, document.getOrDefault("label", " "), TextField.TYPE_STORED));
            doc.add(new Field(LuceneIndex.COMMENT_FIELD, document.getOrDefault("comment", " "), TextField.TYPE_STORED));
            doc.add(new Field(LuceneIndex.CATEGORY_FIELD, document.getOrDefault("category", ""), TextField.TYPE_STORED));
            doc.add(new Field(LuceneIndex.DESCRIPTION_FIELD, document.getOrDefault("description", " "), TextField.TYPE_STORED));
            writer.addDocument(doc);
            this.existence.add(uriPostfix);
            this.insertedEntities.incrementAndGet();
        }
    }

    private void buildDocument(IndexWriter writer, String uri, String label, String comment, String category, String description) throws IOException
    {
        String[] uriTokens = uri.split("/");
        String uriPostfix = uriTokens[uriTokens.length - 1];
        Document doc = new Document();
        doc.add(new Field(LuceneIndex.URI_FIELD, uri, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.COMMENT_FIELD, comment == null ? " " : comment, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.CATEGORY_FIELD, category == null ? " " : category, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.POSTFIX_FIELD, uriPostfix, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.LABEL_FIELD, label == null ? uriPostfix.replace('_', ' ') : label, TextField.TYPE_STORED));
        doc.add(new Field(LuceneIndex.DESCRIPTION_FIELD, description == null ? " " : description, TextField.TYPE_STORED));

        writer.addDocument(doc);
        this.insertedEntities.incrementAndGet();
        this.existence.add(uriPostfix);
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
