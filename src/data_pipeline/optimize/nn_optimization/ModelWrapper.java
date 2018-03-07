package data_pipeline.optimize.nn_optimization;

import data_pipeline.optimize.parameters.HyperParameter;
import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

import java.util.List;
import java.util.StringJoiner;

/**
 * Created by ehallmark on 11/14/17.
 */
public class ModelWrapper<T extends Model> {
    @Getter @Setter
    private T net;
    @Getter @Setter
    private boolean keepTraining;
    @Getter @Setter
    private List<HyperParameter> hyperParameters;

    public ModelWrapper(T net, List<HyperParameter> hyperParameters) {
        this.net=net;
        this.hyperParameters=hyperParameters;
        this.keepTraining=true;
    }

    public String describeHyperParameters() {
        StringJoiner sj = new StringJoiner(", ");
        hyperParameters.forEach(param->{
            sj.add("["+param.getClass().getSimpleName()+"="+param.get()+"]");
        });
        return "Model("+sj.toString()+")";
    }

}
