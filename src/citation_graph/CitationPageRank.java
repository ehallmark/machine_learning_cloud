package citation_graph;

import analysis.tech_tagger.TechTagger;
import org.deeplearning4j.berkeley.Pair;
import page_rank.PageRank;
import seeding.Constants;
import tools.PortfolioList;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/20/17.
 */
public class CitationPageRank extends TechTagger {
    public static Map<String,Set<String>> patentToCitedPatentsMap;
    private static PageRank pageRank;
    private int numEpochs;
    private int depthOfSearch;
    private int timeoutSeconds;
    static {
        patentToCitedPatentsMap=(Map<String,Set<String>>)tryLoadObject(new File("patent_to_cited_patents_map.jobj"));
        pageRank=new PageRank(patentToCitedPatentsMap,0.85,Runtime.getRuntime().availableProcessors());
    }

    public static Object tryLoadObject(File file) {
        System.out.println("Starting to load file: "+file.getName()+"...");
        try {
            if(!file.exists() && new File(Constants.DATA_FOLDER+file.getName()).exists()) file = new File(Constants.DATA_FOLDER+file.getName());
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
            Object toReturn = ois.readObject();
            ois.close();
            System.out.println("Sucessfully loaded "+file.getName()+".");
            return toReturn;
        } catch(Exception e) {
            e.printStackTrace();
            //throw new RuntimeException("Unable to open file: "+file.getPath());
            return null;
        }
    }

    public static PageRank getPageRankModel() { return pageRank; }

    public void setNumEpochs(int numEpochs) { this.numEpochs=numEpochs; }
    public void setDepthOfSearch(int depthOfSearch) { this.depthOfSearch=depthOfSearch; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds=timeoutSeconds; }

    @Override
    public double getTechnologyValueFor(Collection<String> items, String technology, PortfolioList.Type type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        return pageRank.findSimilarDocuments(items,n,numEpochs,depthOfSearch,timeoutSeconds).stream().map(pair->new Pair<>(pair._1,pair._2.doubleValue())).collect(Collectors.toList());
    }

    @Override
    public Collection<String> getAllTechnologies() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return patentToCitedPatentsMap.size();
    }
}
