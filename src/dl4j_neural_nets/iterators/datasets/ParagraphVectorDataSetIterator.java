package dl4j_neural_nets.iterators.datasets;

import analysis.SimilarPatentFinder;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/14/16.
 */
public class ParagraphVectorDataSetIterator implements DataSetIterator {
    private static Random random = new Random(41);
    private int numInputs;
    private int numOutputs;
    private volatile List<String> labels;
    private volatile List<Double> labelProbabilities;
    private volatile Map<String,List<String>> labelsToParagraphIDMap;
    private volatile Map<String,Iterator<String>> labelsToParagraphIDIterator;
    private volatile WeightLookupTable<VocabWord> lookupTable;
    private int batchSize;
    private boolean fallbackToWordVectors;

    // Concatenates vectors for all provided weight lookup tables
    public ParagraphVectorDataSetIterator(Map<String,List<String>> labelsToParagraphIDMap, List<String> orderedLabels, int batchSize, boolean fallbackToWordVectors, WeightLookupTable<VocabWord> lookupTable) {
        if(fallbackToWordVectors) {
            try {
                Database.setupSeedConn();
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        }
        this.numOutputs=orderedLabels.size();
        this.labels=orderedLabels;
        int labelSum = labelsToParagraphIDMap.values().stream().collect(Collectors.summingInt(v->v.size()));
        this.labelProbabilities = this.labels.stream().map(label->labelsToParagraphIDMap.containsKey(label)?(double)labelsToParagraphIDMap.get(label).size()/labelSum:0.0).collect(Collectors.toList());
        assert(Math.abs(labelProbabilities.stream().collect(Collectors.summingDouble(p->p))-1.0) < 0.000001) : "INVALID PROBABILITIES!";
        this.lookupTable=lookupTable;
        this.numInputs=lookupTable.layerSize()*3;
        this.batchSize=batchSize;
        this.labelsToParagraphIDMap=labelsToParagraphIDMap;
        labelsToParagraphIDIterator = new HashMap<>();
        this.fallbackToWordVectors=fallbackToWordVectors;
        setupIterator();
    }

    private void setupIterator() {
        labelsToParagraphIDMap.forEach((k,v)->{
            Collections.shuffle(v);
            // makes sure same cardinality for each dataPoint
            labelsToParagraphIDIterator.put(k,v.iterator());
        });
    }

    private int getRandomLabelIdx() {
        double nextRandom = random.nextDouble();
        int idx = -1;
        double sum = 0.0;
        while(sum <= nextRandom) {
            idx++;
            sum+=labelProbabilities.get(idx);
        }
        assert idx >= 0 : "something went wrong";
        return idx;
    }

    @Override
    public DataSet next(int n) {
        INDArray inputs = Nd4j.create(n,numInputs);
        INDArray outputs = Nd4j.zeros(n,numOutputs);
        for(int i = 0; i < n; i++) {
            if(labelsToParagraphIDIterator.isEmpty()) {
                inputs.putRow(i,Nd4j.zeros(numInputs));
            } else {
                int weightedRandomIdx = getRandomLabelIdx();
                String label = labels.get(weightedRandomIdx);
                Iterator<String> paragraphIDs = labelsToParagraphIDIterator.get(label);

                // remove label from temp map if no more data
                if (paragraphIDs == null || !paragraphIDs.hasNext()) {
                    labelsToParagraphIDIterator.remove(label);
                    // retry
                    i--;
                    continue;
                }

                String paragraphID = paragraphIDs.next();
                INDArray pVector = SimilarPatentFinder.getVectorFromDB(paragraphID, lookupTable);

                if(pVector==null) {
                    // retry
                    i--;
                    continue;
                }

                int labelIdx = labels.indexOf(label);
                inputs.putRow(i, pVector);
                outputs.putScalar(i, labelIdx, 1.0);
            }
        }
        return new DataSet(inputs,outputs);
    }

    @Override
    public int totalExamples() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inputColumns() {
        return numInputs;
    }

    @Override
    public int totalOutcomes() {
        return numOutputs;
    }

    public boolean resetSupported() {
        return true;
    }

    public boolean asyncSupported() {
        return true;
    }

    @Override
    public void reset() {
        setupIterator();
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int numExamples() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getLabels() {
        return labels;
    }

    @Override
    public boolean hasNext() {
        return !labelsToParagraphIDIterator.isEmpty();
    }

    @Override
    public DataSet next() {
        return next(batchSize);
    }
}
