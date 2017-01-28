package analysis;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import server.tools.AbstractAssignee;
import server.tools.AbstractClassCode;
import server.tools.AbstractPatent;
import server.tools.excel.ExcelWritable;
import tools.DistanceFunction;

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

    public Patent(String name, INDArray vector) {
        this.name=name;
        this.vector=vector;
    }

    public static AbstractPatent abstractPatent(Patent old, String reffered) {
        AbstractPatent clone = new AbstractPatent(old.getName(), old.getSimilarityToTarget(), reffered);
        return  clone;
    }

    public static AbstractAssignee abstractAssignee(Patent old, String reffered) {
        AbstractAssignee clone = new AbstractAssignee(old.getName(), old.getSimilarityToTarget(), reffered);
        return clone;
    }


    public static AbstractClassCode abstractClassCode(Patent old, String reffered) {
        AbstractClassCode clone = new AbstractClassCode(old.getName(), old.getSimilarityToTarget(), reffered);
        return clone;
    }

    @Override
    public int compareTo(Patent o) {
        return Double.compare(similarity,Transforms.cosineSim(baseVector,o.vector));
    }

    public double getSimilarityToTarget() {
        return similarity;
    }

    public void calculateSimilarityToTarget(DistanceFunction dist) {
        similarity = dist.distance(baseVector,vector);
    }

    public void setSimilarity(double sim) {
        this.similarity=sim;
    }

    public void incrementSimilarityBy(double sim) {
        similarity+=sim;
    }

    public static void setBaseVector(INDArray baseVector) {
        Patent.baseVector=baseVector;
    }

    public INDArray getVector() {
        return vector;
    }

    public void setVector(INDArray vector) {
        this.vector=vector;
    }
    public String getName() {
        return name;
    }

}
