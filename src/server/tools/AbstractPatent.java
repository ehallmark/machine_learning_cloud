package server.tools;

/**
 * Created by ehallmark on 7/27/16.
 */
public class AbstractPatent implements Comparable<AbstractPatent>{
    protected String name;
    protected double similarity;
    protected String referringName;

    public AbstractPatent(String name, double similarity, String referringName) {
        this.name = name;
        this.similarity=similarity;
        this.referringName=referringName;
    }

    public String getName() {
        return name;
    }

    public double getSimilarity() {
        return similarity;
    }

    public String getReferringName() {
        return referringName;
    }

    public void flipSimilarity() { similarity*=-1.0; }

    @Override
    public int compareTo(AbstractPatent o) {
        return Double.compare(similarity, o.similarity);
    }
}
