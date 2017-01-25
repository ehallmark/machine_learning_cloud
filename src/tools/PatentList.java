package tools;

import analysis.WordFrequencyPair;
import com.google.common.util.concurrent.AtomicDouble;
import edu.stanford.nlp.util.Quadruple;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.berkeley.Triple;
import seeding.Database;
import server.tools.AbstractPatent;
import server.tools.Assignee;
import server.tools.Tag;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList implements Serializable, Comparable<PatentList> {
    private static final long serialVersionUID = 1L;
    private List<AbstractPatent> patents;
    private List<Assignee> assignees;
    private List<Tag> tags;
    private String name1;
    private String name2;
    private double avgSimilarity;
    //
    public PatentList(List<AbstractPatent> patentList, String name1, String name2) {
        this.name1=name1;
        this.name2=name2;
        this.patents=patentList;
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
    public List<Tag> getTags() { return tags; }
    public List<Assignee> getAssignees() { return assignees; }

    public void setPatents(List<AbstractPatent> patents) {
        this.patents=patents;
    }

    public List<String> getPatentStrings() {
        return patents.stream().map(p->p.getName()).collect(Collectors.toList());
    }

    public void filterPortfolioSize(int limit, boolean isPatent) {
        patents=patents.stream()
                .filter(patent->{
                    if(isPatent) {
                        return Database.getAssetCountFor(patent.getAssignee()) <= limit;
                    } else {
                        return Database.getAssetCountFor(patent.getName()) <= limit;
                    }
                }).collect(Collectors.toList());
    }

    public void init(int tagLimit, int tagIndex) {
        patents=patents.stream().filter(patent->!Database.isExpired(patent.getName())).collect(Collectors.toList());
        this.assignees=Assignee.createAssigneesFromPatents(patents,tagIndex);
        this.tags = Tag.createTagsFromPatents(patents,tagLimit);

        Collections.sort(this.assignees);
        Collections.sort(this.tags);

        // similarity is weighted more heavily for more similar matches
        //this.avgSimilarity=patentList.stream().map(p->p.getSimilarity()).collect(Collectors.averagingDouble(p->p.doubleValue()));
        Collections.sort(patents);
        Collections.reverse(patents);

        if(patents.size()>0) {
            this.avgSimilarity = patents.stream().collect(Collectors.averagingDouble(p -> p.getSimilarity()));
        }
        else this.avgSimilarity=0.0d;
    }
}
