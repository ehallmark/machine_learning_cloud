/*-
 *
 *  * Copyright 2016 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package test;

import lombok.Data;
import org.deeplearning4j.nn.conf.graph.GraphVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InvalidInputTypeException;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.Arrays;

/**
 * Adds the ability to reshape and flatten the tensor in the computation graph.<br>
 * NOTE: This class should only be used if you know exactly what you are doing with reshaping activations.
 * Use preprocessors such as {@link org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor} and
 * {@link org.deeplearning4j.nn.conf.preprocessor.FeedForwardToRnnPreProcessor} for most cases.
 *
 * @author Justin Long (crockpotveggies)
 */
@Data
public class ReshapeVertex extends GraphVertex {
    public static final char DEFAULT_RESHAPE_ORDER = 'c';

    protected char reshapeOrder = 'c';
    protected int[] newShape;
    protected int[] maskShape;

    public ReshapeVertex(int... newShape){
        this(newShape, null);
    }

    public ReshapeVertex(@JsonProperty("newShape") int[] newShape,
                         @JsonProperty("maskShape") int[] maskShape) {
        this.reshapeOrder = DEFAULT_RESHAPE_ORDER;
        this.newShape = newShape;
        this.maskShape = maskShape;
    }

    @Override
    public ReshapeVertex clone() {
        return new ReshapeVertex(newShape);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ReshapeVertex))
            return false;
        return Arrays.equals(((ReshapeVertex) o).newShape, newShape);
    }

    @Override
    public int hashCode() {
        return reshapeOrder ^ Arrays.hashCode(newShape);
    }

    @Override
    public int numParams(boolean backprop) {
        return 0;
    }

    @Override
    public org.deeplearning4j.nn.graph.vertex.GraphVertex instantiate(ComputationGraph graph, String name, int idx,
                                                                      INDArray paramsView, boolean initializeParams) {
        return new ReshapeVertexImpl(graph, name, idx, reshapeOrder, newShape, maskShape);
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType... vertexInputs) throws InvalidInputTypeException {
        //Infer output shape from specified shape:
        switch (newShape.length) {
            case 2:
                return InputType.feedForward(newShape[1]);
            case 3:
                return InputType.recurrent(newShape[1]);
            case 4:
                return InputType.convolutional(newShape[2], newShape[3], newShape[1]); //[mb,d,h,w] for activations
            default:
                throw new UnsupportedOperationException(
                        "Cannot infer input type for reshape array " + Arrays.toString(newShape));
        }
    }

}