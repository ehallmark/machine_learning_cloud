package seeding;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.text.documentiterator.FilenamesLabelAwareIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Created by ehallmark on 8/16/16.
 */
public class BuildParagraphVectors {

    public static void createDataFolder(File folder, SentencePreProcessor preProcessor) throws Exception {
        folder.mkdirs();
        BasePatentIterator iter = new BasePatentIterator(Constants.START_DATE);
        while(iter.hasNext()) {
            String nextSentence = iter.nextSentence();
            String label = iter.currentLabel();
            File toMake = new File(folder.getAbsolutePath()+"/"+label);
            BufferedWriter bw = new BufferedWriter(new FileWriter(toMake));
            bw.write(preProcessor.preProcess(nextSentence));
        }
    }


    public static void main(String[] args) throws Exception {
        Database.setupMainConn();
        Database.setupSeedConn();
        File dataFolder = new File(Constants.RAW_PATENT_DATA_FOLDER);
        try {

            if(!dataFolder.exists()) createDataFolder(dataFolder, new MyPreprocessor());

        } finally {
            Database.close();
        }

        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor((token)->token);


        FilenamesLabelAwareIterator iterator = new FilenamesLabelAwareIterator.Builder()
                .addSourceFolder(dataFolder)
                .build();

        System.out.println("Starting paragraph vectors...");

        ParagraphVectors vec = new ParagraphVectors.Builder()
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .iterations(10)
                .epochs(1)
                .layerSize(Constants.VECTOR_LENGTH)
                .learningRate(0.025)
                .minLearningRate(0.0001)
                .batchSize(100)
                .windowSize(5)
                .iterate(iterator)
                .trainWordVectors(false)
                .tokenizerFactory(t)
                .sampling(0)
                .build();

        vec.fit();

        System.out.println("Finished paragraph vectors...");

        /*
            In training corpus we have few lines that contain pretty close words invloved.
            These sentences should be pretty close to each other in vector space
            line 3721: This is my way .
            line 6348: This is my case .
            line 9836: This is my house .
            line 12493: This is my world .
            line 16393: This is my work .
            this is special sentence, that has nothing common with previous sentences
            line 9853: We now have one .
            Note that docs are indexed from 0
         */

        System.out.println("Writing to file...");
        WordVectorSerializer.writeWordVectors(vec, new File(Constants.WORD_VECTORS_PATH));
        System.out.println("Done...");

    }
}
