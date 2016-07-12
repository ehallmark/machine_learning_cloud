package learning;

import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.Word2Vec;

/**
 * Created by ehallmark on 7/12/16.
 */
public class ParagraphBuilder extends ParagraphVectors.Builder {

    public ParagraphVectors.Builder useWordVectors(Word2Vec word2vec) {
        return super.useExistingWordVectors(word2vec);
    }

}

