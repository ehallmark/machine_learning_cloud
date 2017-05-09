package ui_models.attributes.classification.helper;

/**
 * Created by Evan on 2/19/2017.
 */
public class Technology implements Comparable<Technology> {
    public double score;
    public String name;

    public Technology(String name, double score) {
        this.score=score;
        this.name=name;
    }

    @Override
    public int compareTo(Technology o) {
        return Double.compare(score,o.score);
    }
}
