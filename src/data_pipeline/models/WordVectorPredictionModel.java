package data_pipeline.models;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.glove.Glove;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;

import java.io.File;
import java.io.IOException;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class WordVectorPredictionModel<T> extends BaseTrainablePredictionModel<T,SequenceVectors<VocabWord>> {

    public enum Type { Word2Vec, Glove, ParagraphVector, SequenceVector }

    private Type type;
    protected WordVectorPredictionModel(String modelName, Type type) {
        super(modelName);
        this.type=type;
    }


    public abstract File getModelBaseDirectory();

    @Override
    protected void saveNet(SequenceVectors<VocabWord> net, File file) throws IOException {
        if(net instanceof ParagraphVectors) {
            WordVectorSerializer.writeParagraphVectors((ParagraphVectors)net, file);
        } else if(net instanceof Glove) {
            WordVectorSerializer.writeWordVectors((Glove) net, file);
        } else if(net instanceof Word2Vec) {
            WordVectorSerializer.writeWord2VecModel((Word2Vec)net, file);
        } else {
            WordVectorSerializer.writeSequenceVectors(net, new VocabWordFactory(), file);
        }
    }

    @Override
    protected void restoreFromFile(File modelFile) throws IOException {
        if(modelFile!=null&&modelFile.exists()) {
            switch(type) {
                case Glove: {
                    this.net = (Glove)WordVectorSerializer.loadTxtVectors(modelFile);
                    break;
                } case ParagraphVector: {
                    this.net = WordVectorSerializer.readParagraphVectors(modelFile);
                    break;
                } case Word2Vec: {
                    this.net = WordVectorSerializer.readWord2VecModel(modelFile);
                    break;
                } case SequenceVector: {
                    this.net = WordVectorSerializer.readSequenceVectors(new VocabWordFactory(), modelFile);
                    break;
                }
            }
            this.isSaved.set(true);
        } else {
            System.out.println("WARNING: Model file does not exist: "+modelFile.getAbsolutePath());
        }
    }

}
