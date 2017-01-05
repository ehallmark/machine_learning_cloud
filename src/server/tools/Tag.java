package server.tools;

import com.google.common.util.concurrent.AtomicDouble;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public class Tag implements ExcelWritable, Comparable<Tag> {
    private List<AbstractPatent> patents;
    private String name;
    private double avgSimilarity;
    private Double avgValue;

    public Tag(String name, List<AbstractPatent> patents) {
        this.name=name;
        this.patents=patents;
        avgSimilarity=0.0;
        for(AbstractPatent patent : patents) {
            avgSimilarity+=patent.getSimilarity();
            if(patent.getGatherValue()!=null) {
                if(avgValue==null)avgValue=patent.getGatherValue();
                else {
                    avgValue+=patent.getGatherValue();
                }
            }
        }
        avgSimilarity/=patents.size();
        if(avgValue!=null)avgValue/=patents.size();
    }

    public static List<Tag> createTagsFromPatents(List<AbstractPatent> patents, int tagLimit) {
        Map<String,List<AbstractPatent>> tagMap = new HashMap<>();
        for(AbstractPatent patent : patents) {
            patent.getTags().entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(tagLimit).forEach(e->{
                String tag = e.getKey();
                if(tagMap.containsKey(tag)) {
                    tagMap.get(tag).add(patent);
                } else {
                    List<AbstractPatent> list = new ArrayList<>();
                    list.add(patent);
                    tagMap.put(tag,list);
                }
            });
        }
        return tagMap.entrySet().stream().map(e->new Tag(e.getKey(),e.getValue())).collect(Collectors.toList());
    }

    public int get4OrGreaterCount() {
        return patents.stream().collect(Collectors.summingInt(p->p.getGatherValue()>=4.0?1:0));
    }

    public int get3OrGreaterCount() {
        return patents.stream().collect(Collectors.summingInt(p->p.getGatherValue()>=3.0?1:0));
    }

    public int get3OrLessCount() {
        return patents.stream().collect(Collectors.summingInt(p->p.getGatherValue()<3.0?1:0));
    }
    @Override
    public String[] getDataAsRow(boolean valuePrediction, int tagLimit) {
        if(valuePrediction) {
            return new String[]{
                    name,
                    String.valueOf(patents.size()),
                    String.valueOf(avgSimilarity),
                    String.valueOf(avgValue),
                    String.valueOf(get4OrGreaterCount()),
                    String.valueOf(get3OrGreaterCount()),
                    String.valueOf(get3OrLessCount())
            };
        } else {
            return new String[]{
                    name,
                    String.valueOf(patents.size()),
                    String.valueOf(avgSimilarity)
            };
        }
    }

    @Override
    public int compareTo(Tag other) {
        if(other.avgValue!=null&&avgValue!=null) return Double.compare(other.avgValue,avgValue);
        return Double.compare(other.avgSimilarity,avgSimilarity);
    }
}
