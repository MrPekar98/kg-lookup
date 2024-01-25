package dk.aau.dkw.kgservice.index;

import dk.aau.dkw.kgservice.result.Result;
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
    public static final String LABEL_FIELD = "LABEL";
    public static final String COMMENT_FIELD = "COMMENT";
    public static final String CATEGORY_FIELD = "CATEGORY";
    public static final String DESCRIPTION_FIELD = "DESCRIPTION";
    private final Analyzer analyzer;
    private final IndexSearcher searcher;
    private int k;

    public LuceneIndex(Directory luceneDirectory)
    {
        this(luceneDirectory, 10);
    }

    public LuceneIndex(Directory luceneDirectory, int k)
    {
        this.k = k;
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
        try
        {
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            List<String> tokens = tokenize(key);

            for (String token : tokens)
            {
                TermQuery termQuery = new TermQuery(new Term(LABEL_FIELD, token));
                queryBuilder.add(termQuery, BooleanClause.Occur.SHOULD);
            }

            Query q = queryBuilder.build();
            return runQuery(q);
        }

        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
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
        TopScoreDocCollector collector = TopScoreDocCollector.create(this.k, 0);
        this.searcher.search(q, collector);

        ScoreDoc[] hits = collector.topDocs().scoreDocs;
        List<Result> results = new ArrayList<>(this.k);

        for (ScoreDoc doc : hits)
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
