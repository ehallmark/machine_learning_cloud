package tools;

import server.tools.AbstractPatent;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList implements Serializable, Comparable<PatentList> {
    private static final long serialVersionUID = 1L;
    private List<AbstractPatent> patents;
    private String name1;
    private String name2;
    private double avgSimilarity;
    public PatentList(List<AbstractPatent> patentList, String name1, String name2, double avgSimilarity) {
        this.patents=patentList;
        this.name1=name1;
        this.name2=name2;
        this.avgSimilarity=avgSimilarity;
        Collections.sort(patents);
        Collections.reverse(patents);
    }

    public void flipAvgSimilarity() {
        avgSimilarity=avgSimilarity*-1.0;
    }

    public double getAvgSimilarity() {
        return avgSimilarity;
    }

    @Override
    public int compareTo(PatentList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public String getName1() {
        return name1;
    }

    public String getName2() {
        return name2;
    }

    public List<AbstractPatent> getPatents() {
        return patents;
    }

}
