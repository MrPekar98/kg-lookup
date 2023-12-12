package dk.aau.dkw.kgservice.index;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.tdb.TDBFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TDBIndex implements Index<TDBIndex.Query, Set<String>>, AutoCloseable
{
    public record Query(String entity, String predicate) {}

    private File tdbDir;
    private Dataset dataset;
    private Model model;
    private boolean closed = false;

    public TDBIndex(File tdbDirectory)
    {
        this.tdbDir = tdbDirectory;
        this.dataset = TDBFactory.createDataset(this.tdbDir.getAbsolutePath());
        this.model = dataset.getDefaultModel();
    }

    @Override
    public Set<String> get(Query key)
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        Set<String> results = new HashSet<>();
        String query = "SELECT ?o WHERE { <" + key.entity() + "> <" + key.predicate() + "> ?o }";

        try (QueryExecution qExec = QueryExecution.dataset(this.dataset).query(query).build())
        {
            ResultSet rs = qExec.execSelect();

            while (rs.hasNext())
            {
                QuerySolution solution = rs.nextSolution();
                RDFNode node = solution.get("o");

                if (node.isLiteral())
                {
                    results.add(node.asLiteral().getString());
                }
            }

            return results;
        }
    }

    @Override
    public Iterator<Query> keys()
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        String query = "SELECT DISTINCT ?s WHERE { ?s ?p ?o }";

        try (QueryExecution qExec = QueryExecution.dataset(this.dataset).query(query).build())
        {
            ResultSet rs = qExec.execSelect();
            return new KeyIterator(rs, "s");
        }
    }

    @Override
    public void close()
    {
        this.model.close();
        this.dataset.close();
        this.closed = true;
    }

    public static class KeyIterator implements Iterator<Query>
    {
        private final Iterator<String> iterator;

        private KeyIterator(ResultSet resultSet, String subjectVariable)
        {
            Set<String> rs = new HashSet<>();

            while (resultSet.hasNext())
            {
                rs.add(resultSet.next().getResource(subjectVariable).getURI());
            }

            this.iterator = rs.iterator();
        }

        @Override
        public boolean hasNext()
        {
            return this.iterator.hasNext();
        }

        @Override
        public Query next()
        {
            return new Query(this.iterator.next(), "");
        }
    }
}
