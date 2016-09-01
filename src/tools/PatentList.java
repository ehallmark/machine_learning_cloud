package tools;

import analysis.Patent;
import server.tools.AbstractPatent;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList implements Serializable, Comparable<PatentList> {
    private static final long serialVersionUID = 1L;
    private List<AbstractPatent> patents;
    private String name1;
    private String name2;
    private double avgSimilarity;
    public PatentList(List<AbstractPatent> patentList, String name1, String name2) {
        this.patents=patentList;
        this.name1=name1;
        this.name2=name2;
        // similarity is weighted more heavily for more similar matches
        //this.avgSimilarity=patentList.stream().map(p->p.getSimilarity()).collect(Collectors.averagingDouble(p->p.doubleValue()));
        Collections.sort(patents);
        Collections.reverse(patents);

        avgSimilarity = 0;
        if(patentList.size()>0) {
            double total = 0.0;
            int i = 1;
            for(AbstractPatent patent : patents) {
                double multiplier = Math.log(1.0+(new Double(patents.size())/i));
                total+=multiplier;
                avgSimilarity+=(patent.getSimilarity()*multiplier);
                i++;
            }
            avgSimilarity/=total;
        }
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
