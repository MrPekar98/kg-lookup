package dk.aau.dkw.kgservice;

import dk.aau.dkw.kgservice.index.LuceneIndex;
import dk.aau.dkw.kgservice.index.build.LuceneTDBBuilder;
import org.apache.jena.atlas.lib.Pair;
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
    private static final String TDB_DIR = "/tdb";

    public static void main(String[] args)
    {
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

        try
        {
            LuceneTDBBuilder luceneBuilder = new LuceneTDBBuilder(new File(TDB_DIR), new File(LUCENE_DIR));
            luceneBuilder.build();

            long duration = System.currentTimeMillis() - start;
            duration = (duration / 1000) / 60;
            System.out.println("Finished in " + duration + " m");

            return ResponseEntity.ok("Indexed KG files in " + duration + " m\n");
        }

        catch (RuntimeException e)
        {
            long duration = System.currentTimeMillis() - start;
            duration = (duration / 1000) / 60;
            System.err.println("Failed in " + duration + " m: " + e.getMessage());

            return ResponseEntity.badRequest().body("Exception thrown after " + duration + " m: " + e.getMessage() + "\n");
        }
    }

    @GetMapping(value = "/search")
    public ResponseEntity<String> search(@RequestParam(value = "query") String query, @RequestParam(value = "k", defaultValue = "10") int k)
    {
        long start = System.currentTimeMillis();
        query = query.replace("%20", " ");
        System.out.println("Query: " + query);

        try (Directory dir = FSDirectory.open(new File(LUCENE_DIR).toPath()))
        {
            LuceneIndex lucene = new LuceneIndex(dir, k);
            List<Pair<String, Double>> results = lucene.get(query);
            StringBuilder resultBuilder = new StringBuilder();

            for (Pair<String, Double> result : results)
            {
                resultBuilder.append(result.getLeft()).append(" - ").append(result.getRight()).append(",");
            }

            return ResponseEntity.ok(resultBuilder.toString());
        }

        catch (IOException | RuntimeException e)
        {
            long duration = System.currentTimeMillis() - start;
            duration = duration / 1000;
            System.err.println("IOException when searching after " + duration + " s: " + e.getMessage());

            return ResponseEntity.badRequest().body("Exception thrown after " + duration + " s: " + e.getMessage());
        }
    }
}
