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
import java.util.function.Consumer;

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
    public void forEach(Consumer<Query> consumer)
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        String query = "SELECT DISTINCT ?s WHERE { ?s ?p ?o }";

        try (QueryExecution qExec = QueryExecution.dataset(this.dataset).query(query).build())
        {
            ResultSet rs = qExec.execSelect();

            while (rs.hasNext())
            {
                String uri = rs.next().getResource("s").getURI();
                Query key = new Query(uri, "");
                consumer.accept(key);
            }
        }
    }

    @Override
    public void close()
    {
        this.model.close();
        this.dataset.close();
        this.closed = true;
    }

    /**
     * Iterator of a single variable in a given result set
     * WARNING: This class does not work, as it closes the iterator once the scope has changed.
     *          So, this can't be used in keys().
     */
    public static class KeyIterator implements Iterator<Query>
    {
        private final String sub;
        private final Iterator<QuerySolution> iter;

        private KeyIterator(ResultSet resultSet, String subjectVariable)
        {
            this.sub = subjectVariable;
            this.iter = resultSet;
        }

        @Override
        public boolean hasNext()
        {
            return this.iter.hasNext();
        }

        @Override
        public Query next()
        {
            if (!hasNext())
            {
                return null;
            }

            String uri = this.iter.next().getResource(this.sub).getURI();
            return new Query(uri, "");
        }
    }
}
