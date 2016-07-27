package analysis;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import server.AbstractPatent;

import java.io.Serializable;

/**
 * Created by ehallmark on 7/26/16.
 */
public class Patent implements Comparable<Patent>, Serializable {
    private static final long serialVersionUID = 1L;
    private INDArray vector;
    private String name;
    private double similarity;
    private static INDArray baseVector;
    private String assignee;

    public Patent(String name, INDArray vector) {
        this.name=name;
        this.vector=vector;
    }

    public static AbstractPatent abstractClone(Patent old, String assigneeName) {
        AbstractPatent clone = new AbstractPatent(old.getName(), old.getSimilarityToTarget(), assigneeName);
        return  clone;
    }


    @Override
    public int compareTo(Patent o) {
        return Double.compare(similarity,Transforms.cosineSim(baseVector,o.vector));
    }

    public double getSimilarityToTarget() {
        return similarity;
    }

    public double getSimilarityTo(INDArray otherVector) {
        return Transforms.cosineSim(otherVector,vector);
    }

    public void calculateSimilarityToTarget() {
        System.out.println("Starting to calculate similarity");
        similarity = Transforms.cosineSim(baseVector,vector);
        System.out.println("Finished calculating similarity");
    }

    public static void setBaseVector(INDArray baseVector) {
        Patent.baseVector=baseVector;
    }

    public void setAssignee(String assignee) {
        this.assignee=assignee;
    }

    public String getAssignee() {
        assert assignee!=null : "Please set assignee name!";
        return assignee;
    }

    public String getName() {
        return name;
    }
}
