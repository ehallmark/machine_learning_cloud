package citation_graph;

import analysis.tech_tagger.TechTagger;
import citation_graph.page_rank.SimRank;
import model.edges.Edge;
import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import tools.PortfolioList;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Created by ehallmark on 4/20/17.
 */
public class CitationPageRank extends TechTagger {
    public static final Map<String,Set<String>> patentToCitedPatentsMap;
    private static final Map<Edge,Float> rankTable;
    static {
        patentToCitedPatentsMap=(Map<String,Set<String>>)tryLoadObject(new File("patent_to_cited_patents_map.jobj"));
        if(SimRankHelper.file.exists()) {
            rankTable = new SimRank.Loader().loadRankTable(SimRankHelper.file);
        } else {
            System.out.println("WARNING: Rank table file does not exist");
            rankTable=null;
        }
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

    @Override
    public double getTechnologyValueFor(Collection<String> items, String technology, PortfolioList.Type type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        List<util.Pair<String,Float>> data = SimRank.findSimilarDocumentsFromRankTable(rankTable,items,n);
        List<Pair<String,Double>> toReturn = new ArrayList<>(n);
        data.forEach(p->toReturn.add(new Pair<>(p._1,p._2.doubleValue())));
        return toReturn;
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
