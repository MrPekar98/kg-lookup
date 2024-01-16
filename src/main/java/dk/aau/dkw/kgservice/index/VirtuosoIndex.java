package dk.aau.dkw.kgservice.index;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import virtuoso.jena.driver.VirtGraph;
import virtuoso.jena.driver.VirtuosoQueryExecution;
import virtuoso.jena.driver.VirtuosoQueryExecutionFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

public class VirtuosoIndex extends GraphIndex implements Index<GraphIndex.Query, Set<String>>, AutoCloseable
{
    private VirtGraph graph;
    private boolean closed = false;

    public VirtuosoIndex(String host, int port)
    {
        String url = "jdbc:virtuoso://" + host + ":" + port;
        this.graph = new VirtGraph(url, "dba", "dba");
    }

    @Override
    protected Set<String> execGet(String query)
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        VirtuosoQueryExecution exec = VirtuosoQueryExecutionFactory.create(query, this.graph);
        ResultSet rs = exec.execSelect();
        Set<String> results = new HashSet<>();

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

    @Override
    public Iterator<GraphIndex.Query> keys()
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        String query = "SELECT DISTINCT ?s WHERE { ?s ?p ?o }";
        VirtuosoQueryExecution exec = VirtuosoQueryExecutionFactory.create(query, this.graph);
        ResultSet rs = exec.execSelect();

        return new KeyIterator(rs, "s");
    }

    @Override
    public void forEach(Consumer<GraphIndex.Query> consumer)
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        String query = "SELECT DISTINCT ?s WHERE { ?s ?p ?o }";
        VirtuosoQueryExecution exec = VirtuosoQueryExecutionFactory.create(query, this.graph);
        ResultSet rs = exec.execSelect();

        while (rs.hasNext())
        {
            QuerySolution solution = rs.nextSolution();
            String uri = solution.getResource("s").getURI();
            GraphIndex.Query key = new GraphIndex.Query(uri, "");
            consumer.accept(key);
        }
    }

    @Override
    public void close()
    {
        this.graph.close();
    }
}
