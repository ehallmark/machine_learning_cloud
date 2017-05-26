package ui_models.attributes.classification;

import seeding.patent_view_api.Patent;
import seeding.patent_view_api.PatentAPIHandler;
import ui_models.attributes.classification.helper.BuildCPCToGatherStatistics;
import ui_models.attributes.classification.helper.Technology;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import tools.ClassCodeHandler;
import tools.MinHeap;
import ui_models.portfolios.AbstractPortfolio;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/18/2017.
 */
public class CPCGatherTechTagger extends ClassificationAttr {
    protected Map<String, INDArray> technologyMap;
    protected List<String> orderedTechnologies;
    protected static Map<String, INDArray> DEFAULT_TECHNOLOGY_MAP;
    protected static List<String> DEFAULT_ORDERED_TECHNOLOGIES;
    protected static final int MIN_CLASS_CODE_LENGTH = 7;
    protected static final CPCGatherTechTagger model = new CPCGatherTechTagger();

    public static CPCGatherTechTagger get() { return model; }
    private static Map<String, INDArray> loadTechMap() {
        if (DEFAULT_TECHNOLOGY_MAP == null)
            DEFAULT_TECHNOLOGY_MAP = (Map<String, INDArray>) Database.tryLoadObject(BuildCPCToGatherStatistics.techMapFile);
        return DEFAULT_TECHNOLOGY_MAP;
    }

    private static List<String> loadTechList() {
        if (DEFAULT_ORDERED_TECHNOLOGIES == null)
            DEFAULT_ORDERED_TECHNOLOGIES = (List<String>) Database.tryLoadObject(BuildCPCToGatherStatistics.techListFile);
        return DEFAULT_ORDERED_TECHNOLOGIES;
    }

    private CPCGatherTechTagger() {
        this(DEFAULT_TECHNOLOGY_MAP == null ? loadTechMap() : DEFAULT_TECHNOLOGY_MAP, DEFAULT_ORDERED_TECHNOLOGIES == null ? loadTechList() : DEFAULT_ORDERED_TECHNOLOGIES);
    }

    protected CPCGatherTechTagger(Map<String, INDArray> techMap, List<String> orderedTech) {
        this.technologyMap = techMap;
        this.orderedTechnologies = orderedTech;
    }

    public int numClassifications() {
        return orderedTechnologies.size();
    }

    public Collection<String> getClassifications() { return new ArrayList<>(orderedTechnologies); }

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int n) {
        Collection<String> items = portfolio.getTokens();
        List<INDArray> vectors = new ArrayList<>(items.size());
        items.forEach(item -> {
            INDArray v = technologyMap.get(item);
            if (v != null) vectors.add(v);
        });
        if (vectors.isEmpty()) return Collections.emptyList();
        INDArray vec;
        if (vectors.size() == 1) vec = vectors.get(0).dup();
        else vec = Nd4j.vstack(vectors).mean(0);
        return topTechnologies(vec, n);
    }

    public Collection<String> getAllTechnologies() {
        return new ArrayList<>(orderedTechnologies);
    }


    private List<Pair<String, Double>> topTechnologies(INDArray vec, int n) {
        if (vec == null) return Collections.emptyList();
        MinHeap<Technology> heap = new MinHeap<>(n);
        for (int i = 0; i < orderedTechnologies.size(); i++) {
            String tech = orderedTechnologies.get(i);
            heap.add(new Technology(tech, vec.getDouble(i)));
        }
        List<Pair<String, Double>> predictions = new ArrayList<>(n);
        while (!heap.isEmpty()) {
            Technology tech = heap.remove();
            predictions.add(0, new Pair<>(tech.name, tech.score));
        }
        return predictions;
    }

    public static ClassificationAttr trainAndSaveLatestModel(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> testData, Map<String, Collection<String>> validationData) {
        int minPatentSize = 20;
        int minCPCLength = MIN_CLASS_CODE_LENGTH;
        double decayRate = 2d;
        List<String> orderedTechnologies = new ArrayList<>(trainingData.keySet());
        // relevant cpc subgroup to patent map
        Collection<String> patentsToQuery = new HashSet<>();
        trainingData.forEach((tech,patents)->{
            if(patents.size()>=minPatentSize)patentsToQuery.addAll(patents);
        });

        Collection<Patent> allPatents= PatentAPIHandler.requestAllPatents(patentsToQuery);
        if(allPatents.isEmpty()) throw new RuntimeException("Unable to find any patents...");
        System.out.println("Finished loading data");
        Map<String,Collection<String>> allCPCsToPatentsMap = new HashMap<>();
        allPatents.forEach(patent->{
            patent.getClassCodes().forEach(cpc->{
                if(cpc.getSubgroup()==null)return;
                String cpcStr = ClassCodeHandler.convertToLabelFormat(cpc.getSubgroup()).trim();
                while(cpcStr.length()>=minCPCLength) {
                    if(allCPCsToPatentsMap.containsKey(cpcStr)) {
                        allCPCsToPatentsMap.get(cpcStr).add(patent.getPatentNumber());
                    } else {
                        Set<String> set = new HashSet<>();
                        set.add(patent.getPatentNumber());
                        allCPCsToPatentsMap.put(cpcStr,set);
                    }
                    cpcStr=cpcStr.substring(0,cpcStr.length()-1).trim();
                }
            });
        });
        System.out.println("Finished adding cpcs to map");
        Collection<String> allClassCodes = new HashSet<>(allCPCsToPatentsMap.keySet());

        Map<String,INDArray> classCodeToCondProbMap = new HashMap<>();

        BuildCPCToGatherStatistics.handleCPCS(allClassCodes,orderedTechnologies,trainingData,allCPCsToPatentsMap,classCodeToCondProbMap);

        System.out.println("Finished handling cpcs...");

        // get values for each patent and assignee
        Collection<String> patents = Database.getCopyOfAllPatents();
        Map<String,INDArray> map = new HashMap<>();
        patents.forEach(patent->{
            INDArray probVec = BuildCPCToGatherStatistics.predictTechnologiesVectorForPatent(orderedTechnologies,classCodeToCondProbMap,patent,decayRate,minCPCLength);
            if(probVec!=null) {
                map.put(patent,probVec);
            }
        });


        // add assignees
        Map<String,INDArray> assigneeMap = new HashMap<>();
        Database.getAssignees().forEach(assignee->{
            Collection<String> assigneePatents = new HashSet<>();
            assigneePatents.addAll(Database.selectPatentNumbersFromAssignee(assignee));
            if(assigneePatents.isEmpty()) return;
            List<String> patentList=assigneePatents.stream().filter(patent->map.containsKey(patent)).collect(Collectors.toList());
            if(patentList.isEmpty()) return;
            INDArray avg = Nd4j.create(patentList.size(),orderedTechnologies.size());
            for(int i = 0; i < patentList.size(); i++) {
                avg.putRow(i,map.get(patentList.get(i)).dup());
            }
            assigneeMap.put(assignee,avg.mean(0));
            System.out.println("Added probabilities for assignee: "+assignee);
        });

        map.putAll(assigneeMap);

        // Adding CPCS to MAP
        //System.out.println("Added assignees; now adding CPCs");
        //map.putAll(classCodeToCondProbMap);

        Database.trySaveObject(map,BuildCPCToGatherStatistics.techMapFile);
        // save ordered list too
        Database.trySaveObject(orderedTechnologies,BuildCPCToGatherStatistics.techListFile);

        // update data
        DEFAULT_ORDERED_TECHNOLOGIES=orderedTechnologies;
        DEFAULT_TECHNOLOGY_MAP=map;

        return new CPCGatherTechTagger();
    }

    public static void main(String[] args) {
        System.out.println(loadTechList());
    }

}