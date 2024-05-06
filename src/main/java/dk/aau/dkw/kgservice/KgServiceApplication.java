package dk.aau.dkw.kgservice;

import dk.aau.dkw.kgservice.index.LuceneIndex;
import dk.aau.dkw.kgservice.index.VirtuosoIndex;
import dk.aau.dkw.kgservice.index.build.LuceneBuilder;
import dk.aau.dkw.kgservice.index.build.LuceneFileBuilder;
import dk.aau.dkw.kgservice.index.build.LuceneGraphBuilder;

import dk.aau.dkw.kgservice.result.JsonSerializer;
import dk.aau.dkw.kgservice.result.Result;
import dk.aau.dkw.kgservice.result.XmlSerializer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
public class KgServiceApplication implements WebServerFactoryCustomizer<ConfigurableWebServerFactory>
{
    private static final String LUCENE_DIR = "/lucene";
    private static final String KG_DIR = "/kg";
    private static final String LOG_DIR = "/logs";
    private static final String VIRTUOSO_URL = "http://" + System.getenv("VIRTUOSO") + ":8890/sparql";
    private static final String VIRTUOSO_GRAPH_NAME = "http://localhost:8890/" + System.getenv("GRAPH");
    private static Directory dir;
    private static boolean isLoading = false;

    public static void main(String[] args) throws IOException
    {
        File logDir = new File(LOG_DIR);
        logDir.mkdir();

        dir = FSDirectory.open(new File(LUCENE_DIR).toPath());
        SpringApplication.run(KgServiceApplication.class, args);
    }

    private static void logIndexing(int insertedEntities, Map<String, String> skippedEntities)
    {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_DIR + "/index.log")))
        {
            writer.write("INSERTED ENTITIES: " + insertedEntities + "\n");

            for (Map.Entry<String, String> entry : skippedEntities.entrySet())
            {
                writer.write(entry.getKey() + ": " + entry.getValue() + "\n");
            }

            writer.flush();
        }

        catch (IOException e)
        {
            throw new RuntimeException("Failed logging: " + e.getMessage());
        }
    }

    @Override
    public void customize(ConfigurableWebServerFactory factory)
    {
        factory.setPort(7000);
    }

    @GetMapping(value = "/index")
    public ResponseEntity<String> index(@RequestParam(value = "domain", defaultValue = "") String domain,
                                        @RequestParam(value = "inmemory", defaultValue = "false") boolean inMemory)
    {
        if (isLoading)
        {
            return ResponseEntity.badRequest().body("Indexes are currently being constructed");
        }

        else if (inMemory)
        {
            System.out.println("Indexing in-memory");
        }

        String entityDomain = !domain.isEmpty() ? domain : null;
        long start = System.currentTimeMillis();
        isLoading = true;
        System.out.println("Constructing indexes...");

        try (VirtuosoIndex graph = new VirtuosoIndex(VIRTUOSO_URL, VIRTUOSO_GRAPH_NAME))
        {
            LuceneBuilder luceneBuilder = inMemory ? new LuceneFileBuilder(new File(LUCENE_DIR), new File(KG_DIR), entityDomain, true) :
                    new LuceneGraphBuilder(graph, new File(KG_DIR), new File(LUCENE_DIR), entityDomain, true);
            luceneBuilder.build();

            long duration = System.currentTimeMillis() - start;
            duration = (duration / 1000) / 60;
            isLoading = false;
            logIndexing(luceneBuilder.insertedEntities(), luceneBuilder.skippedEntities());
            System.out.println("Finished in " + duration + " m");
            System.out.println("Inserted entities: " + luceneBuilder.insertedEntities());
            System.out.println("Skipped entities: " + luceneBuilder.skippedEntities().size());

            return ResponseEntity.ok("Indexed KG files in " + duration + "m\nInserted entities: " +
                    luceneBuilder.insertedEntities() + "\nSkipped entities: " + luceneBuilder.skippedEntities().size() + "\n");
        }

        catch (RuntimeException e)
        {
            long duration = System.currentTimeMillis() - start;
            duration = (duration / 1000) / 60;
            isLoading = false;
            System.err.println("Failed in " + duration + " m: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.badRequest().body("Exception thrown after " + duration + "m: " + e.getMessage() + "\n");
        }
    }

    @GetMapping(value = "/search")
    public ResponseEntity<String> search(@RequestParam(value = "query") String query, @RequestParam(value = "k", defaultValue = "10") int k,
                                         @RequestParam(value = "format", defaultValue = "json") String format, @RequestParam(value = "fuzzy", defaultValue = "false") boolean useFuzzy)
    {
        long start = System.currentTimeMillis();
        query = query.replace("%20", " ");
        System.out.println("Query: " + query);

        try
        {
            LuceneIndex lucene = new LuceneIndex(dir, k, useFuzzy);
            List<Result> results = lucene.get(query);
            String serialized = switch (format) {
                case "json" -> new JsonSerializer(results).serialize();
                case "xml" -> new XmlSerializer(results).serialize();
                default -> "null";
            };

            System.out.println("Query took " + (System.currentTimeMillis() - start) + "ms");
            return ResponseEntity.ok(serialized);
        }

        catch (RuntimeException e)
        {
            long duration = System.currentTimeMillis() - start;
            duration = duration / 1000;
            System.err.println("Exception when searching after " + duration + " s: " + e.getMessage());

            return ResponseEntity.badRequest().body("Exception thrown after " + duration + "s: " + e.getMessage());
        }
    }

    @GetMapping(value = "/exists")
    public ResponseEntity<Boolean> debug(@RequestParam(value = "entity") String entity)
    {
        try
        {
            LuceneIndex lucene = new LuceneIndex(dir, false);
            boolean exists = lucene.check(entity);
            System.out.println(entity + " exists: " + exists);

            return ResponseEntity.ok(exists);
        }

        catch (RuntimeException e)
        {
            return ResponseEntity.badRequest().build();
        }
    }
}
