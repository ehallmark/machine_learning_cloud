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

    public static Patent clone(Patent old) {
        Patent clone = new Patent(old.getName(), old.getVector());
        clone.setSimilarity(old.getSimilarityToTarget());
        return  clone;
    }

    private void setSimilarity(double similarity) {
        this.similarity=similarity;
    }

    private INDArray getVector() {
        return vector;
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
        similarity = Transforms.cosineSim(baseVector,vector);
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
