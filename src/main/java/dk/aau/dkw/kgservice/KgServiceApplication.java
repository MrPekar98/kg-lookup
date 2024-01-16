package dk.aau.dkw.kgservice;

import dk.aau.dkw.kgservice.index.LuceneIndex;
import dk.aau.dkw.kgservice.index.VirtuosoIndex;
import dk.aau.dkw.kgservice.index.build.LuceneBuilder;
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

import java.io.File;
import java.io.IOException;
import java.util.List;

@SpringBootApplication
@RestController
public class KgServiceApplication implements WebServerFactoryCustomizer<ConfigurableWebServerFactory>
{
    private static final String LUCENE_DIR = "/lucene";
    private static final String VIRTUOSO_URL = "http://" + System.getenv("VIRTUOSO") + ":8890/sparql";
    private static final String VIRTUOSO_GRAPH_NAME = "http://localhost:8890/" + System.getenv("GRAPH");
    private static Directory dir;

    public static void main(String[] args) throws IOException
    {
        dir = FSDirectory.open(new File(LUCENE_DIR).toPath());
        SpringApplication.run(KgServiceApplication.class, args);
    }

    @Override
    public void customize(ConfigurableWebServerFactory factory)
    {
        factory.setPort(7000);
    }

    @GetMapping(value = "/index")
    public ResponseEntity<String> index()
    {
        long start = System.currentTimeMillis();
        System.out.println("Constructing indexes...");

        try (VirtuosoIndex graph = new VirtuosoIndex(VIRTUOSO_URL, VIRTUOSO_GRAPH_NAME))
        {
            LuceneBuilder luceneBuilder = new LuceneGraphBuilder(graph, new File(LUCENE_DIR));
            luceneBuilder.build();

            long duration = System.currentTimeMillis() - start;
            duration = (duration / 1000) / 60;
            System.out.println("Finished in " + duration + " m");

            return ResponseEntity.ok("Indexed KG files in " + duration + "m\n");
        }

        catch (RuntimeException e)
        {
            long duration = System.currentTimeMillis() - start;
            duration = (duration / 1000) / 60;
            System.err.println("Failed in " + duration + " m: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.badRequest().body("Exception thrown after " + duration + "m: " + e.getMessage() + "\n");
        }
    }

    @GetMapping(value = "/search")
    public ResponseEntity<String> search(@RequestParam(value = "query") String query, @RequestParam(value = "k", defaultValue = "10") int k, @RequestParam(value = "format", defaultValue = "json") String format)
    {
        long start = System.currentTimeMillis();
        query = query.replace("%20", " ");
        System.out.println("Query: " + query);

        try
        {
            LuceneIndex lucene = new LuceneIndex(dir, k);
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
}
