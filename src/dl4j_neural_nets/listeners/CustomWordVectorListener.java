package dl4j_neural_nets.listeners;

import dl4j_neural_nets.tests.ModelEvaluator;
import dl4j_neural_nets.tests.TestAnalogies;
import edu.stanford.nlp.util.Triple;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.glove.Glove;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.enums.ListenerEvent;
import org.deeplearning4j.models.sequencevectors.interfaces.VectorsListener;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 12/1/16.
 */
public class CustomWordVectorListener implements VectorsListener<VocabWord> {
    private int lines;
    private String[] words;
    private List<Triple<String,String,String>> analogies;
    private static org.slf4j.Logger log = LoggerFactory.getLogger(CustomWordVectorListener.class);
    private long currentEpoch = 0;
    private String filepath;
    private String modelName;


    public CustomWordVectorListener(File file, String modelName, int lines, List<Triple<String,String,String>> analogies, String... words) {
        this.lines=lines;
        this.analogies=analogies;
        this.words=words;
        this.modelName=modelName;
        this.filepath=file==null?null:file.getAbsolutePath();
    }

    @Override
    public boolean validateEvent(ListenerEvent event, long argument) {
        switch (event) {
            case EPOCH: {
                if(argument!=currentEpoch) {
                    currentEpoch = argument;
                    //return true;
                }
                break;

            } case ITERATION: {
                break;

            } case LINE: {
                if(argument%lines==0) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    @Override
    public void processEvent(ListenerEvent event, SequenceVectors<VocabWord> sequenceVectors, long argument) {
        if (event.equals(ListenerEvent.LINE)) {
            if (filepath != null) {
                if (sequenceVectors instanceof Glove) {
                    WordVectorSerializer.writeWordVectors((Glove) sequenceVectors, filepath + currentEpoch);
                } else if (sequenceVectors instanceof ParagraphVectors) {
                    WordVectorSerializer.writeParagraphVectors((ParagraphVectors) sequenceVectors, filepath + argument);
                } else {
                    WordVectorSerializer.writeWord2VecModel((Word2Vec) sequenceVectors, filepath + currentEpoch);
                }
            }
            try {
                // Evaluate model
                String currentName = modelName + "[EPOCH "+currentEpoch+", LINE " + argument + "]";
                System.out.println(currentName);
               // String stats = new ModelEvaluator().evaluateWordVectorModel(sequenceVectors.getLookupTable(),currentName);
                //System.out.println(stats);
            } catch(Exception e) {
                e.printStackTrace();
            }
            log.info("Line: " + argument);
            // Prints out the closest 10 words to "day". An example on what to do with these Word Vectors.
            log.info("Closest Words:");
            for (String word : words) {
                Collection<String> lst = sequenceVectors.wordsNearest(word, 10);
                System.out.println("10 Words closest to '" + word + "': " + lst);

            }
            if (analogies != null) {
                TestAnalogies test = new TestAnalogies(sequenceVectors, analogies, 10);
                test.printAnalogies();
            }
        }
    }
}
