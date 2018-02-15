package models.similarity_models.combined_similarity_model;

import models.NDArrayHelper;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.File;

public class TestDeepEncodingModel {
    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        DeepCPC2VecEncodingPipelineManager.setCudaEnvironment();

        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        int nEpochs = 3;


        DeepCPC2VecEncodingPipelineManager pipelineManager = DeepCPC2VecEncodingPipelineManager.getOrLoadManager(rebuildDatasets);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);

        MultiDataSetIterator iterator = pipelineManager.getDatasetManager().getValidationIterator();
        double score = 0d;
        int count = 0;
        while(iterator.hasNext()) {
            INDArray features = iterator.next().getFeatures(0);
            INDArray mean = features.mean(2);
            double s = 0d;
            for(int i = 0; i < features.shape()[2]; i++) {
                double sum = NDArrayHelper.sumOfCosineSimByRow(mean,features.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(i)));
                s+=sum/features.shape()[2];
            }
            System.out.print("-");

            count+=features.shape()[0];
            score+=s;
        }
        System.out.println();
        score = 1d - (score/count);
        System.out.println("Test score: "+score);
    }

}
