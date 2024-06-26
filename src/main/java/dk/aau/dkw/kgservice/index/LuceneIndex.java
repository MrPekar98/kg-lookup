package dk.aau.dkw.kgservice.index;

import dk.aau.dkw.kgservice.result.Result;
import org.apache.jena.atlas.lib.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class LuceneIndex implements Index<String, List<Result>>
{
    public static final String URI_FIELD = "URI";
    public static final String POSTFIX_FIELD = "POSTFIX";
    public static final String LABEL_FIELD = "LABEL";
    public static final String COMMENT_FIELD = "COMMENT";
    public static final String CATEGORY_FIELD = "CATEGORY";
    public static final String DESCRIPTION_FIELD = "DESCRIPTION";
    private final Analyzer analyzer;
    private final IndexSearcher searcher;
    private int k;
    private final boolean useFuzzy;

    public LuceneIndex(Directory luceneDirectory, boolean useFuzzy)
    {
        this(luceneDirectory, 50, useFuzzy);
    }

    public LuceneIndex(Directory luceneDirectory, int k, boolean useFuzzy)
    {
        this.k = k;
        this.useFuzzy = useFuzzy;
        this.analyzer = new StandardAnalyzer();

        try
        {
            DirectoryReader dirReader = DirectoryReader.open(luceneDirectory);
            this.searcher = new IndexSearcher(dirReader);
        }

        catch (IOException e)
        {
            throw new IllegalArgumentException("IOException: Lucene index directory '" + luceneDirectory.toString() + "'");
        }
    }

    public void setK(int k)
    {
        this.k = k;
    }

    @Override
    public List<Result> get(String key)
    {
        return get(key, true);
    }

    public List<Result> get(String key, boolean usePostfix)
    {
        List<Pair<String, Float>> fields = new ArrayList<>(List.of(new Pair<>(LABEL_FIELD, 10.0f),
                new Pair<>(COMMENT_FIELD, 0.1f), new Pair<>(DESCRIPTION_FIELD, 0.1f)));

        if (usePostfix)
        {
            fields.add(new Pair<>(POSTFIX_FIELD, 20.0f));
        }

        return search(key, fields);
    }

    private List<Result> search(String key, List<Pair<String, Float>> fields)
    {
        try
        {
            List<String> tokens = tokenize(key);
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

            for (var field : fields)
            {
                BooleanQuery.Builder tokenQueryBuilder = new BooleanQuery.Builder();

                for (String token : tokens)
                {
                    Term term = new Term(field.getLeft(), token);
                    Query query = this.useFuzzy ? new FuzzyQuery(term) : new TermQuery(term);
                    tokenQueryBuilder.add(new BoostQuery(query, field.getRight()), BooleanClause.Occur.SHOULD);
                }

                queryBuilder = queryBuilder.add(tokenQueryBuilder.build(), BooleanClause.Occur.SHOULD);
            }

            Query query = queryBuilder.build();
            return runQuery(query);
        }

        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    public boolean check(String entity)
    {
        try
        {
            String[] tokens = entity.split("/");
            String postfix = tokens[tokens.length - 1];
            FuzzyQuery query = new FuzzyQuery(new Term(POSTFIX_FIELD, postfix));
            List<Result> results = runQuery(query);

            for (Result result : results)
            {
                if (entity.equals(result.uri()))
                {
                    return true;
                }
            }

            return false;
        }

        catch (IOException e)
        {
            return false;
        }
    }

    private List<String> tokenize(String str) throws IOException
    {
        List<String> tokens = new ArrayList<>();
        TokenStream tokenStream = this.analyzer.tokenStream(null, str);
        CharTermAttribute attribute = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();

        while (tokenStream.incrementToken())
        {
            tokens.add(attribute.toString());
        }

        tokenStream.close();
        return tokens;
    }

    private List<Result> runQuery(Query q) throws IOException
    {
        TopDocs docs = this.searcher.search(q, this.k);
        List<Result> results = new ArrayList<>(this.k);

        for (ScoreDoc doc : docs.scoreDocs)
        {
            Document document = this.searcher.doc(doc.doc);
            results.add(new Result(document.get(URI_FIELD), document.get(LABEL_FIELD), document.get(DESCRIPTION_FIELD), doc.score));
        }

        return results;
    }

    @Override
    public Iterator<String> keys()
    {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void forEach(Consumer<String> consumer)
    {
        throw new UnsupportedOperationException("Unsupported");
    }
}
