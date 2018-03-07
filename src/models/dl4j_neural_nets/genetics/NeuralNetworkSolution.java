package models.dl4j_neural_nets.genetics;

import models.genetics.Solution;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.jetbrains.annotations.NotNull;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

/**
 * Created by Evan on 6/11/2017.
 */
public class NeuralNetworkSolution implements Solution {
    private MultiLayerNetwork network;
    private DataSetIterator iterator;
    private INDArray testMatrix;
    public NeuralNetworkSolution(MultiLayerNetwork network, DataSetIterator iterator, INDArray testMatrix) {
        this.iterator=iterator;
        this.network=network;
        this.testMatrix=testMatrix;
    }

    @Override
    public double fitness() {
        return 0;
    }

    @Override
    public void calculateFitness() {
    }

    @Override
    public Solution mutate() {
        return null;
    }

    @Override
    public Solution crossover(Solution other) {
        return null;
    }

    @Override
    public int compareTo(@NotNull Solution o) {
        return 0;
    }
}
