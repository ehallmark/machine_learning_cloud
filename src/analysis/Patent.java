package analysis;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import server.tools.AbstractPatent;

import java.io.Serializable;

/**
 * Created by ehallmark on 7/26/16.
 */
public class Patent implements Comparable<Patent>, Serializable {
    private static final long serialVersionUID = 1L;
    private INDArray vector;
    private String name;
    private double similarity;
    private Type type;
    private static INDArray baseVector;
    private String referringName;
    private static Type sortType;
    public enum Type { ALL, CLAIM, ABSTRACT, DESCRIPTION, TITLE, CLASS, SUBCLASS }

    public Patent(String name, INDArray vector, Type type) {
        this.name=name;
        this.vector=vector;
        this.type=type;
    }

    public static AbstractPatent abstractClone(Patent old, String reffered) {
        AbstractPatent clone = new AbstractPatent(old.getName(), old.getSimilarityToTarget(), reffered);
        return  clone;
    }

    public void setSimilarityToTarget(double similarity) {
        this.similarity=similarity;
    }


    @Override
    public int compareTo(Patent o) {
        if(!sortType.equals(Type.ALL) && !sortType.equals(type)) return -1; // handles the sort type
        return Double.compare(similarity,Transforms.cosineSim(baseVector,o.vector));
    }

    public double getSimilarityToTarget() {
        return similarity;
    }

    public void calculateSimilarityToTarget() {
        similarity = Transforms.cosineSim(baseVector,vector);
    }

    public static void setBaseVector(INDArray baseVector) {
        Patent.baseVector=baseVector;
    }

    public static void setSortType(Type type) {
        Patent.sortType=type;
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
