package dk.aau.dkw.kgservice.index;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import java.util.*;

public abstract class GraphIndex implements Index<GraphIndex.Query, Set<String>>
{
    public record Query(String entity, String predicate) {}

    public record Triple(String subject, String predicate, String object) {}

    protected String graphUri;

    protected GraphIndex(String graphUri)
    {
        this.graphUri = graphUri;
    }

    @Override
    public Set<String> get(GraphIndex.Query key)
    {
        if (key.entity() == null || key.entity().contains("@") || key.entity().contains("?"))
        {
            return new HashSet<>();
        }

        String query = "SELECT ?o WHERE { GRAPH <" + this.graphUri + "> { <" + key.entity() + "> <" + key.predicate() + "> ?o } }";
        return execGet(query);
    }

    public Set<Map<String, String>> batchGet(Set<String> uris, Map<String, String> predicateLabels)
    {
        boolean anyUris = false;
        StringBuilder queryString = new StringBuilder("SELECT ?uri (GROUP_CONCAT(?_label; separator = \" - \") AS ?label) ");
        predicateLabels.forEach((key, value) -> queryString
                                                .append("(GROUP_CONCAT(?_")
                                                .append(value)
                                                .append("; separator = \" - \") AS ?")
                                                .append(value)
                                                .append(") "));
        queryString.append("WHERE { GRAPH <").append(this.graphUri).append("> { VALUES ?uri { ");

        for (String uri : uris)
        {
            if (uri.contains("@") || uri.contains("?"))
            {
                continue;
            }

            queryString.append("<").append(uri).append("> ");
            anyUris = true;
        }

        if (!anyUris)
        {
            return new HashSet<>();
        }

        queryString.append("} ?uri <http://www.w3.org/2000/01/rdf-schema#label> ?_label . ");

        for (String predicate : predicateLabels.keySet())
        {
            queryString.append("OPTIONAL { ?uri <").append(predicate).append("> ?_").append(predicateLabels.get(predicate))
                    .append(" . FILTER(LANGMATCHES(LANG(?_").append(predicateLabels.get(predicate)).append("), \"en\")) } ");
        }

        queryString.append("FILTER(LANGMATCHES(LANG(?_label), \"en\")) . } } GROUP BY ?uri");
        return execBatchGet(queryString.toString());
    }

    protected abstract Set<String> execGet(String query);
    protected abstract Set<Map<String, String>> execBatchGet(String query);

    /**
     * Iterator of a single variable in a given result set
     * WARNING: This class does not work, as it closes the iterator once the scope has changed.
     *          So, this can't be used in keys().
     */
    public static class KeyIterator implements Iterator<Query>
    {
        private final String sub;
        private final Iterator<QuerySolution> iter;

        KeyIterator(ResultSet rs, String subjectVariable)
        {
            this.sub = subjectVariable;
            this.iter = rs;
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
