package dk.aau.dkw.kgservice.index;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;

import java.nio.charset.MalformedInputException;
import java.util.*;
import java.util.function.Consumer;

public class VirtuosoIndex extends GraphIndex implements Index<GraphIndex.Query, Set<String>>, AutoCloseable
{
    private String url;
    private boolean closed = false;

    public VirtuosoIndex(String endpointUrl, String graphName)
    {
        super(graphName);
        this.url = endpointUrl;
        this.graphUri = graphName;
    }

    @Override
    protected Set<String> execGet(String query)
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        try
        {
            QueryExecution exec = QueryExecutionFactory.sparqlService(this.url, query);
            ResultSet rs = exec.execSelect();
            Set<String> results = new HashSet<>();

            while (rs.hasNext())
            {
                QuerySolution solution = rs.nextSolution();
                RDFNode node = solution.get("o");

                if (node.isLiteral())
                {
                    results.add(node.toString().split("@")[0]);
                }
            }

            return results;
        }

        catch (Exception e)
        {
            return Collections.emptySet();
        }
    }

    @Override
    protected Set<Map<String, String>> execBatchGet(String query)
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        try
        {
            QueryExecution exec = QueryExecutionFactory.sparqlService(this.url, query);
            ResultSet rs = exec.execSelect();
            Set<Map<String, String>> results = new HashSet<>();

            while (rs.hasNext())
            {
                QuerySolution solution = rs.nextSolution();
                Map<String, String> result = new HashMap<>();

                for (Iterator<String> it = solution.varNames(); it.hasNext();)
                {
                    String varName = it.next();
                    RDFNode valueNode = solution.get(varName);
                    result.put(varName, valueNode.isLiteral() ? valueNode.toString().split("@")[0] : valueNode.toString());
                }

                results.add(result);
            }

            return results;
        }

        catch (Exception e)
        {
            return Collections.emptySet();
        }
    }

    @Override
    public Iterator<GraphIndex.Query> keys()
    {
        if (this.closed)
        {
            throw new IllegalStateException("Index is closed");
        }

        String query = "SELECT DISTINCT ?s WHERE { GRAPH <" + super.graphUri + "> { ?s ?p ?o } }";
        QueryExecution exec = QueryExecutionFactory.sparqlService(this.url, query);
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

        String query = "SELECT DISTINCT ?s WHERE { GRAPH <" + super.graphUri + "> { ?s ?p ?o } }";
        QueryExecution exec = QueryExecutionFactory.sparqlService(this.url, query);
        ResultSet rs = exec.execSelect();

        while (rs.hasNext())
        {
            QuerySolution solution = rs.nextSolution();
            String uri = solution.getResource("s").getURI();
            GraphIndex.Query key = new GraphIndex.Query(uri, "");
            consumer.accept(key);
        }

        exec.close();
    }

    @Override
    public void close()
    {
        this.closed = true;
    }
}
