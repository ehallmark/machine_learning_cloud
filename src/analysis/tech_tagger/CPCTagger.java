package analysis.tech_tagger;

import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import tools.MinHeap;
import tools.PortfolioList;

import java.util.*;

/**
 * Created by Evan on 2/18/2017.
 */
public class CPCTagger extends TechTagger {
    protected Map<String,INDArray> technologyMap;
    protected List<String> orderedTechnologies;
    protected static final Map<String,INDArray> DEFAULT_TECHNOLOGY_MAP;
    protected static final List<String> DEFAULT_ORDERED_TECHNOLOGIES;
    static {
        DEFAULT_TECHNOLOGY_MAP=(Map<String,INDArray>)Database.tryLoadObject(BuildCPCToGatherStatistics.techMapFile);
        DEFAULT_ORDERED_TECHNOLOGIES=(List<String>)Database.tryLoadObject(BuildCPCToGatherStatistics.techListFile);

    }

    public CPCTagger() {
        this(DEFAULT_TECHNOLOGY_MAP,DEFAULT_ORDERED_TECHNOLOGIES);
    }

    protected CPCTagger(Map<String,INDArray> techMap, List<String> orderedTech) {
        this.technologyMap=techMap;
        this.orderedTechnologies=orderedTech;
    }


    public List<Pair<String,Double>> getTechnologiesFor(String item, PortfolioList.Type type, int n) {
        return topTechnologies(handlePortfolioType(item,type),n);
    }

    public double getTechnologyValueFor(String item, String technology) {
        int idx = orderedTechnologies.indexOf(technology);
        if(idx>=0) {
            INDArray array = technologyMap.get(item);
            if(array!=null&&array.length()>idx) {
                return array.getDouble(idx);
            } else {
                return 0d;
            }
        }else return 0d;
    }

    public List<Pair<String,Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        List<INDArray> vectors = new ArrayList<>(items.size());
        items.forEach(item->{
            INDArray v = handlePortfolioType(item,type);
            if(v!=null)vectors.add(v);
        });
        if(vectors.isEmpty()) return Collections.emptyList();
        INDArray vec;
        if(vectors.size()==1) vec = vectors.get(0).dup();
        else vec = Nd4j.vstack(vectors).mean(0);
        return topTechnologies(vec,n);
    }

    @Override
    public Collection<String> getAllTechnologies() {
        return new HashSet<>(orderedTechnologies);
    }

    private INDArray handlePortfolioType(String name, PortfolioList.Type type) {
        if(name==null||type==null)return null;

        INDArray vec=null;
        if(type.equals(PortfolioList.Type.assignees)) {
            Collection<String> assignees = Database.possibleNamesForAssignee(name);
            if(assignees.isEmpty()) return null;
            Map<String,Integer> assigneeMap = new HashMap<>();
            assignees.forEach(assignee->{
               if(technologyMap.containsKey(assignee)) {
                   assigneeMap.put(assignee,Database.getExactAssetCountFor(assignee));
               }
            });
            if(assigneeMap.isEmpty()) return null;
            String bestBet = assigneeMap.entrySet().stream().max((a1,a2)->Integer.compare(a1.getValue(),a2.getValue())).get().getKey();
            vec = technologyMap.get(bestBet);
        } else if (type.equals(PortfolioList.Type.patents)) {
            vec = technologyMap.get(name);
        } else if (type.equals(PortfolioList.Type.class_codes)) {
            String prefix = name.trim();
            while(prefix.length()>=BuildCPCToGatherStatistics.MIN_CLASS_CODE_LENGTH) {
                if(technologyMap.containsKey(prefix)) {
                    vec = technologyMap.get(prefix);
                    break;
                }
                prefix = prefix.substring(0,prefix.length()-1).trim();
            }
        } else return null;
        return vec;
    }

    private List<Pair<String,Double>> topTechnologies(INDArray vec, int n) {
        if(vec==null)return Collections.emptyList();
        MinHeap<Technology> heap = new MinHeap<>(n);
        for(int i = 0; i < orderedTechnologies.size(); i++) {
            String tech = orderedTechnologies.get(i);
            heap.add(new Technology(tech,vec.getDouble(i)));
        }
        List<Pair<String,Double>> predictions = new ArrayList<>(n);
        while(!heap.isEmpty()) {
            Technology tech = heap.remove();
            predictions.add(0,new Pair<>(tech.name,tech.score));
        }
        return predictions;
    }

}
