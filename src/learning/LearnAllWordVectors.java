package learning;

import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import tools.FileLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import seeding.Constants;

import java.io.File;

/**
 * Created by ehallmark on 7/14/16.
 */
public class LearnAllWordVectors extends LearnCompdbWordVectors {


    public static void main(String[] args) throws Exception {
        File paragraphVectorFile = new File(Constants.COMPDB_PARAGRAPH_VECTORS);
        int numOutputs = 200;
        TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(token->token);
        LabelAwareIterator labelAwareIterator = new FileLabelAwareIterator.Builder()
                .addSourceFolder(new File(Constants.COMPDB_TRAIN_FOLDER))
                .build();


        LearnAllWordVectors app = new LearnAllWordVectors();
        app.initializeVariables(labelAwareIterator,numOutputs,tokenizerFactory,paragraphVectorFile);
        //app.loadOrCreateAndSaveVocabCache();
        //app.loadOrCreateAndSaveWordVectors();
        app.loadOrCreateAndSaveParagraphVectors();
    }
}
