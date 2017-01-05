package server.tools;

import com.google.common.util.concurrent.AtomicDouble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public class Assignee implements Comparable<Assignee>, ExcelWritable {
    private Double avgValue;
    private double avgSimilarity;
    private String name;
    private List<AbstractPatent> patents;
    private List<String> tags;

    public static List<Assignee> createAssigneesFromPatents(List<AbstractPatent> patents, int tagIdx) {
        Map<String,List<AbstractPatent>> assigneeMap = new HashMap<>();
        for(AbstractPatent patent : patents) {
            if(assigneeMap.containsKey(patent.getFullAssignee())) {
                assigneeMap.get(patent.getFullAssignee()).add(patent);
            } else {
                List<AbstractPatent> list = new ArrayList<>();
                list.add(patent);
                assigneeMap.put(patent.getFullAssignee(),list);
            }
        }
        return assigneeMap.entrySet().stream().map(e->new Assignee(e.getKey(),e.getValue(),tagIdx)).collect(Collectors.toList());
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

    public Assignee(String name, List<AbstractPatent> patents, int tagIdx) {
        this.name=name;
        this.patents=patents;
        avgSimilarity=0.0;
        Map<String,AtomicDouble> tagMap = new HashMap<>();
        for(AbstractPatent patent : patents) {
            avgSimilarity+=patent.getSimilarity();
            if(patent.getGatherValue()!=null) {
                if(avgValue==null)avgValue=patent.getGatherValue();
                else {
                    avgValue+=patent.getGatherValue();
                }
            }
            patent.getTags().entrySet().forEach(e->{
                String tag = e.getKey();
                double sim = e.getValue();
                if(tagMap.containsKey(tag)) {
                    tagMap.get(tag).getAndAdd(sim);
                } else {
                    tagMap.put(tag,new AtomicDouble(sim));
                }
            });
        }
        tags = tagMap.entrySet().stream().sorted((e1,e2)->Double.compare(e2.getValue().get(),e1.getValue().get())).map(e->e.getKey()).collect(Collectors.toList());
        avgSimilarity/=patents.size();
        if(avgValue!=null)avgValue/=patents.size();

    }
    public List<AbstractPatent> getPatents() {
        return patents;
    }

    public int getAssetCount() {
        return patents.size();
    }

    public int getTagCount() {
        return tags.size();
    }
    public Double getAvgValue() {
        return avgValue;
    }

    public double getAvgSimilarity() {
        return avgSimilarity;
    }

    public String getName() {
        return name;
    }

    @Override
    public int compareTo(Assignee other) {
        if(other.getAvgValue()!=null&&avgValue!=null) return Double.compare(other.getAvgValue(),avgValue);
        return Double.compare(other.getAvgSimilarity(),avgSimilarity);
    }

    @Override
    public String[] getDataAsRow(boolean valuePrediction, int tagLimit) {
        if(valuePrediction) {
            return new String[]{
                    name,
                    String.valueOf(getAssetCount()),
                    String.valueOf(avgSimilarity),
                    String.valueOf(avgValue),
                    String.valueOf(Math.min(getTagCount(),tagLimit)),
                    tags.isEmpty() ? "" : tags.get(0),
                    tags.size() > 1 ? String.join("; ", tags.subList(1, Math.min(tagLimit,tags.size()))) : ""
            };
        } else {
            return new String[]{
                    name,
                    String.valueOf(getAssetCount()),
                    String.valueOf(avgSimilarity),
                    String.valueOf(Math.min(getTagCount(),tagLimit)),
                    tags.isEmpty() ? "" : tags.get(0),
                    tags.size() > 1 ? String.join("; ", tags.subList(1, Math.min(tagLimit,tags.size()))) : ""
            };
        }
    }
}
