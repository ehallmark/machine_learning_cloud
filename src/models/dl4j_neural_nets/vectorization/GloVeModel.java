package models.dl4j_neural_nets.vectorization;

import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import models.dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seeding.Constants;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 12/19/16.
 */
public class GloVeModel {
    private static final Logger log = LoggerFactory.getLogger(GloVeModel.class);
    public static final File gloVeFile = new File("claims_test.glove");

    public static void main(String[] args) throws Exception {
        SequenceIterator<VocabWord> iter = DatabaseIteratorFactory.GatherTechnologySequenceIterator(0,1000000);
        long time0 = System.currentTimeMillis();
        GlobalDocumentVectors model = new GlobalDocumentVectors.Builder()
                .iterate(iter)
                .layerSize(100)
                .useAdaGrad(true)
                .windowSize(4)
                .minWordFrequency(10)
                .resetModel(true)
                .epochs(1)
                .iterations(50)
                .workers(8)
                .stopWords(new ArrayList<String>(Constants.STOP_WORD_SET))
                .batchSize(1000)
                .xMax(100.0)
                .alpha(0.75)
                .learningRate(0.05)
                //.maxMemory(10)
                .setVectorsListeners(Arrays.asList(new CustomWordVectorListener(gloVeFile,"GloDV Example",1000,null,"7455590","medicine","doctor","market","companies","statistics")))
                .shuffle(false) // usually bad/slow
                .symmetric(true)
                .build();

        model.fit();

        //double simD = model.similarity("day", "night");
        //log.info("Day/night similarity: " + simD);

        long time1 = System.currentTimeMillis();

        System.out.println("Time to run model: "+((double)(time1-time0))/(1000*60)+" minutes");

        System.out.print("Saving model...");
        WordVectorSerializer.writeWordVectors(model,gloVeFile);

        // tests
        wordsNearest(model,"claim","artificial","intelligence","medicine","doctor","market","companies","statistics");

        System.exit(0);
    }


    private static void wordsNearest(SequenceVectors<VocabWord> model, String... words) {
        for(String word: words) {
            Collection<String> similar = model.wordsNearest(word, 10);
            log.info("Nearest words to '" + word + "': " + similar);
        }
    }
}
