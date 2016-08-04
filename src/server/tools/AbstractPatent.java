package server.tools;

/**
 * Created by ehallmark on 7/27/16.
 */
public class AbstractPatent {
    protected String name;
    protected double similarity;
    public AbstractPatent(String name, double similarity) {
        this.name = name;
        this.similarity=similarity;
    }
    public String getName() {
        return name;
    }

    public double getSimilarity() {
        return similarity;
    }
}
