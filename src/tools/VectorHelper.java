package tools;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import seeding.*;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/20/16.
 */
public class VectorHelper {
    private static TokenizerFactory tokenizerFactory;
    private static VocabCache<VocabWord> vocab;
    private static double LOG_N;
    private static int N;
    static {
        tokenizerFactory=new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static void setupVocab(File vocabFile) {
        try {
            vocab = WordVectorSerializer.readVocabCache(vocabFile);
            N = vocab.totalNumberOfDocs();
            LOG_N = Math.log(1+N);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static Double[][] compute2DAvgWordVectorsFrom(WordVectors wordVectors, String[] sentences) {
        Double[][] data = new Double[sentences.length][];
        int count = 0;
        for(String sentence : sentences) {
            Double[] vec = computeAvgWordVectorsFrom(wordVectors, sentence);
            data[count] = vec;
            if(vec!=null) {
                count++;
            }
        }
        if(count > 0) return Arrays.copyOfRange(data, 0, count);
        else return null;
    }

    /*public static Double[][] createAndMerge2DWordVectors(WordVectors wordVectors, String[] sentences) {
        if(sentences==null)return null;
        Double[][] data;

        List<INDArray> validSentences = new ArrayList<>(sentences.length);

        for(String sentence : sentences) {
            if (sentence == null) continue;
            List<String> tokens = createAndPrefilterTokens(wordVectors, sentence);
            if (!tokens.isEmpty()) validSentences.add(TFIDFcentroidVector(wordVectors, tokens));
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
                    matrix.put(new int[]{row, col}, allSentences.getScalar(new int[]{sentence,row,col}));
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
    }*/

    public static Double[] computeAvgWordVectorsFrom(WordVectors wordVectors, String sentence) {
        Double[] data = null;
        if(sentence!=null) {
            List<String> tokens = createAndPrefilterTokens(wordVectors,sentence);
            if(!tokens.isEmpty())data = toObject(TFIDFcentroidVector(wordVectors, tokens).data().asDouble());
        }
        return data;
    }

    private static List<String> createAndPrefilterTokens(WordVectors wordVectors, String sentence) {
        List<String> tokens = tokenizerFactory.create(sentence).getTokens();
        // filter
        tokens.removeIf(token->(token==null || Constants.STOP_WORD_SET.contains(token) || !wordVectors.hasWord(token)));
        return tokens;
    }

    // MAKE SURE ALL TOKENS EXIST IN THE VOCABULARY!!!
    private static INDArray TFIDFcentroidVector(WordVectors wordVectors, List<String> tokens) {
        INDArray allWords = Nd4j.create(tokens.size(), Constants.VECTOR_LENGTH);
        double total = 0.0;
        AtomicInteger cnt = new AtomicInteger(0);
        for (String token : tokens) {
            double invDocFreq = (vocab.hasToken(token)) ? Math.log(1+((double)N/Math.max(1,vocab.docAppearedIn(token)))) : LOG_N;
            System.out.println("Inverse Document Frequency: "+invDocFreq);
            total+=invDocFreq;
            assert invDocFreq > 0 && invDocFreq < Double.POSITIVE_INFINITY;
            allWords.putRow(cnt.getAndIncrement(), wordVectors.getWordVectorMatrix(token).mul(invDocFreq));
        }
        INDArray mean = allWords.mean(0).div(total/tokens.size());
        System.out.println("Total: "+total);
        System.out.println(mean.toString());
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

    public static int[] toPrim(Integer[] objArray) {
        if(objArray==null) return null;
        int[] vec = new int[objArray.length];
        int i = 0;
        for(int d: objArray) {
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
        if(titleText!=null) titleThread = new VectorBuilderThread(wordVectors, titleText);
        if(titleThread!=null) {
            titleThread.fork();
        }

        // Abstract
        String abstractText = resultSet.getString(4);
        VectorBuilderThread abstractThread = null;
        if(!shouldRemoveSentence(abstractText)) abstractThread = new VectorBuilderThread(wordVectors, abstractText);
        if(abstractThread!=null) {
            abstractThread.fork();
        }

        // Description
        String descriptionText = resultSet.getString(5);
        VectorBuilderThread descriptionThread = null;
        if(!shouldRemoveSentence(descriptionText)) descriptionThread = new VectorBuilderThread(wordVectors, descriptionText);
        if(descriptionThread!=null) {
            descriptionThread.fork();
        }

        if(titleThread!=null)p.setTitleWordVectors(titleThread.get());
        if(abstractThread!=null)p.setAbstractWordVectors(abstractThread.get());
        if(descriptionThread!=null)p.setDescriptionWordVectors(descriptionThread.get());

        return p;
    }

    public static boolean shouldRemoveSentence(String str) {
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
            if(wordCount >= Constants.MIN_WORDS_PER_SENTENCE) return false;
        }
        return true;
    }

    private static Double[] flatten2Dto1D(Double[][] array2D) {
        if(array2D==null || array2D.length==0)return null;
        int innerLength = array2D[0].length;
        Double[] array1D = new Double[array2D.length*innerLength];
        for(int i = 0; i < array2D.length; i++) {
            for(int j = 0; j < innerLength; j++) {
                array1D[i*innerLength+j]=array2D[i][j];
            }
        }
        return array1D;
    }

    public static INDArray extractResultSetToVector(ResultSet results) throws SQLException {
        return extractResultSetToVector(results, 0);
    }

    public static INDArray extractResultSetToVector(ResultSet results, int offset) throws SQLException {
        return extractResultSetToVector(results, Constants.NUM_1D_VECTORS, Constants.NUM_2D_VECTORS, offset);
    }

    private static INDArray extractResultSetToVector(ResultSet results, int num1DVectors, int num2DVectors, int offset) throws SQLException {
        double[] values = null;
        for (int i = 1+offset; i <= num1DVectors+offset; i++) {
            double[] nextValues = VectorHelper.toPrim((Double[])results.getArray(i).getArray());
            if(values==null) values = nextValues;
            else values = concat(values, nextValues);
        }
        for (int i = num1DVectors+1+offset; i <=num1DVectors+num2DVectors+offset; i++) {
            Double[][] array2D = (Double[][])results.getArray(i).getArray();
            double[] nextValues = VectorHelper.toPrim(flatten2Dto1D(array2D));
            if(values==null) values = nextValues;
            else values = concat(values, nextValues);
        }
        return Nd4j.create(values);
    }

    private static double[] concat(double[] a, double[] b) {
        int aLen = a.length;
        int bLen = b.length;
        double[] c = new double[aLen+bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

}
