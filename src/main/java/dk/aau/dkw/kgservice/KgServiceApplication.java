package dk.aau.dkw.kgservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class KgServiceApplication implements WebServerFactoryCustomizer<ConfigurableWebServerFactory>
{
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
    public ResponseEntity<String> index(@RequestParam(value = "dir") String dir)
    {
        long start = System.currentTimeMillis();

        long duration = System.currentTimeMillis() - start;
        duration = ((duration / 1000) / 60) / 60;
        return ResponseEntity.ok("Indexed KG files in " + duration + " h");
    }

    @GetMapping(value = "/search")
    public ResponseEntity<String> search(@RequestParam(value = "query") String query)
    {
        return ResponseEntity.ok("Your query: " + query);
    }
}
