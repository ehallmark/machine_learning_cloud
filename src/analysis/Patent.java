package analysis;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.io.Serializable;

/**
 * Created by ehallmark on 7/26/16.
 */
public class Patent implements Comparable<Patent>, Serializable {
    private static final long serialVersionUID = 1L;
    private INDArray vector;
    private String name;
    private static INDArray baseVector;
    private String assignee;
    private double similarity;

    public Patent(String name, INDArray vector) {
        this.name=name;
        this.vector=vector;
    }

    @Override
    public int compareTo(Patent o) {
        assert baseVector!=null : "Please set base vector before comparing!";
        Double sim1 = Transforms.cosineSim(baseVector,vector);
        Double sim2 = Transforms.cosineSim(baseVector,o.vector);
        return sim1.compareTo(sim2);
    }

    public double getSimilarityToTarget() {
        return similarity;
    }

    public void calculateSimilarityToTarget() {
        assert baseVector!=null: "Please set base vector!";
        similarity = Transforms.cosineSim(baseVector,vector);
    }

    public static void setBaseVector(INDArray baseVector) {
        Patent.baseVector=baseVector;
    }

    public void setAssignee(String assignee) {
        this.assignee=assignee;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getName() {
        return name;
    }
}
