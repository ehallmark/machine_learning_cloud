package seeding;

import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by ehallmark on 8/25/16.
 */
public class EtsiLabelledIterator extends DatabaseLabelledIterator {
    public EtsiLabelledIterator(VocabCache<VocabWord> vocabCache, Set<String> stopWords) throws SQLException {
        this.vocabCache=vocabCache;
        this.stopWords=stopWords;
        if(this.stopWords==null)this.stopWords=new HashSet<>();
        preProcessor=(t)->t;
        resultSet = Database.selectRawPatents(Constants.ETSI_PATENT_LIST);
    }

    public EtsiLabelledIterator(VocabCache<VocabWord> vocabCache) throws SQLException {
        this(vocabCache,null);
    }

    public EtsiLabelledIterator() throws SQLException {
        this(null);
    }

    @Override
    public void reset() {
        try {
            resultSet = Database.selectRawPatents(Constants.ETSI_PATENT_LIST);
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
    }

}
