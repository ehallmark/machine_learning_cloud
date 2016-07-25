package tools;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import seeding.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/20/16.
 */
public class VectorHelper {
    private static TokenizerFactory tokenizerFactory;
    static {
        tokenizerFactory=new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static Double[][] compute2DAvgWordVectorsFrom(WordVectors wordVectors, String sentence) {
        Double[][] data = null;

        if(sentence!=null) {
            List<String> tokens = createAndPrefilterTokens(wordVectors,sentence);
            if(tokens.isEmpty()) return null;

            data = new Double[Constants.NUM_ROWS_OF_WORD_VECTORS][];

            final int bucketSize = Math.max(1, tokens.size() / Constants.NUM_ROWS_OF_WORD_VECTORS);
            for (int i = 0; i < Constants.NUM_ROWS_OF_WORD_VECTORS; i++) {
                int begin = i * bucketSize;
                int end = Math.min(tokens.size(), i * bucketSize + bucketSize);
                if(begin>=end) begin=end-1;
                List<String> subList = tokens.subList(begin, end);
                INDArray wordVector = centroidVector(wordVectors, subList);
                data[i] = toObject(wordVector.data().asDouble());
            }

        }
        return data;

    }

    public static Double[][] createAndMerge2DWordVectors(WordVectors wordVectors, String[] sentences) {
        if(sentences==null)return null;
        Double[][] data;

        List<INDArray> validSentences = new ArrayList<>(sentences.length);

        for(String sentence : sentences) {
            if (sentence == null) continue;
            List<String> tokens = createAndPrefilterTokens(wordVectors, sentence);
            if (!tokens.isEmpty()) validSentences.add(centroidVector(wordVectors, tokens));
        }
        if(validSentences.isEmpty()) return null;

        INDArray allSentences = Nd4j.create(validSentences.size(), Constants.NUM_ROWS_OF_WORD_VECTORS, Constants.VECTOR_LENGTH);
        AtomicInteger cnt = new AtomicInteger(0);

        validSentences.forEach(sentence->{
            int index = cnt.getAndIncrement();
            for(int row = 0; row < sentence.rows(); row++) {
                INDArray rowArray = sentence.getRow(row);
                for(int col=0; col < sentence.columns(); col++) {
                    allSentences.put(new int[]{index,row,col},rowArray.getScalar(col));
                }
            }
        });

        data = new Double[Constants.NUM_ROWS_OF_WORD_VECTORS][];

        INDArray mean = Nd4j.zeros(Constants.NUM_ROWS_OF_WORD_VECTORS, Constants.VECTOR_LENGTH);
        for(int sentence = 0; sentence < validSentences.size(); sentence++) {
            INDArray matrix = Nd4j.create(Constants.NUM_ROWS_OF_WORD_VECTORS, Constants.VECTOR_LENGTH);
            for(int row = 0; row < matrix.rows(); row++) {
                for(int col=0; col<matrix.columns(); col++) {
                    matrix.put(new int[]{sentence, row, col}, allSentences.getScalar(new int[]{sentence,row,col}));
                }
            }
            mean.addi(matrix);
        }
        mean.divi(validSentences.size());

        assert mean.rows()==Constants.NUM_ROWS_OF_WORD_VECTORS && mean.columns()==Constants.VECTOR_LENGTH;

        for(int row = 0; row < Constants.NUM_ROWS_OF_WORD_VECTORS; row++) {
            Double[] innerRow = toObject(mean.getRow(0).data().asDouble());
            data[row] = innerRow;
        }
        return data;
    }

    public static Double[] computeAvgWordVectorsFrom(WordVectors wordVectors, String sentence) {
        Double[] data = null;
        if(sentence!=null) {
            List<String> tokens = createAndPrefilterTokens(wordVectors,sentence);
            if(!tokens.isEmpty())data = toObject(centroidVector(wordVectors, tokens).data().asDouble());
        }
        return data;
    }

    private static List<String> createAndPrefilterTokens(WordVectors wordVectors, String sentence) {
        List<String> tokens = tokenizerFactory.create(sentence).getTokens();
        // filter
        tokens.removeIf(token->(token==null || !wordVectors.hasWord(token)));
        return tokens;
    }

    // MAKE SURE ALL TOKENS EXIST IN THE VOCABULARY!!!
    private static INDArray centroidVector(WordVectors wordVectors, List<String> tokens) {
        INDArray allWords = Nd4j.create(tokens.size(), Constants.VECTOR_LENGTH);
        AtomicInteger cnt = new AtomicInteger(0);
        for (String token : tokens) {
            allWords.putRow(cnt.getAndIncrement(), wordVectors.getWordVectorMatrix(token));
        }
        INDArray mean = allWords.mean(0);
        return mean;
    }

    private static Double[] toObject(double[] primArray) {
        if(primArray==null) return null;
        Double[] vec = new Double[primArray.length];
        int i = 0;
        for(double d: primArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

    public static double[] toPrim(Double[] objArray) {
        if(objArray==null) return null;
        double[] vec = new double[objArray.length];
        int i = 0;
        for(double d: objArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }


    public static PatentVectors getPatentVectors(ResultSet resultSet, WordVectors wordVectors) throws SQLException, InterruptedException, ExecutionException {
        // Pub Doc Number
        String pubDocNumber = resultSet.getString(1);

        // Publication Date
        Integer pubDate = resultSet.getInt(2);

        PatentVectors p = new PatentVectors(pubDocNumber,pubDate);

        // Invention Title
        String titleText = resultSet.getString(3);
        VectorBuilderThread titleThread = null;
        if(!shouldRemoveSentence(titleText)) titleThread = new VectorBuilderThread(wordVectors, titleText);
        if(titleThread!=null) {
            titleThread.fork();
        }

        // Abstract
        String abstractText = resultSet.getString(4);
        VectorBuilderThread2D abstractThread = null;
        if(!shouldRemoveSentence(abstractText)) abstractThread = new VectorBuilderThread2D(wordVectors, abstractText);
        if(abstractThread!=null) {
            abstractThread.fork();
        }

        // Description
        String descriptionText = resultSet.getString(5);
        VectorBuilderThread2D descriptionThread = null;
        if(!shouldRemoveSentence(descriptionText)) descriptionThread = new VectorBuilderThread2D(wordVectors, descriptionText);
        if(descriptionThread!=null) {
            descriptionThread.fork();
        }

        if(titleThread!=null)p.setTitleWordVectors(titleThread.get());
        if(abstractThread!=null)p.setAbstractWordVectors(abstractThread.get());
        if(descriptionThread!=null)p.setDescriptionWordVectors(descriptionThread.get());

        return p;
    }

    private static boolean shouldRemoveSentence(String str) {
        if(str==null)return true;
        boolean wasChar = false;
        int wordCount = 0;
        for(Character c : str.toCharArray()) {
            if(Character.isSpaceChar(c) && wasChar) {
                wordCount++;
                wasChar = false;
            } else if(Character.isAlphabetic(c)) {
                wasChar = true;
            }
            if(wordCount >= 1) return false;
        }
        return true;
    }

}
