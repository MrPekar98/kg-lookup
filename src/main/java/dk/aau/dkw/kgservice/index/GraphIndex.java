package dk.aau.dkw.kgservice.index;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class GraphIndex implements Index<GraphIndex.Query, Set<String>>
{
    public record Query(String entity, String predicate) {}

    @Override
    public Set<String> get(GraphIndex.Query key)
    {
        if (key.entity() == null || key.entity().contains("@") || key.entity().contains("?"))
        {
            return new HashSet<>();
        }

        Set<String> results = new HashSet<>();
        String query = "SELECT ?o WHERE { <" + key.entity() + "> <" + key.predicate() + "> ?o }";
        return execGet(query);
    }

    protected abstract Set<String> execGet(String query);

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

            //String uri = this.iter.next().getResource(this.sub).getURI();
            return new Query("uri", "");
        }
    }
}
