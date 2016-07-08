package seeding;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;

/**
 * Created by ehallmark on 6/24/16.
 */
public class DatabaseIterator implements LabelAwareIterator {

    private String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private static String compDBUrl = "jdbc:postgresql://192.168.1.148/compdb_production?user=postgres&password=&tcpKeepAlive=true";
    private static Connection seedConn;
    private static Connection mainConn;
    private static Connection compDBConn;
    private Map<String,List<String>> patentToTechnologyHash;
    private int numPatents;
    private int numTechnologies;
    private int cursor;
    private String currentPatent;

    private List<String> currentLabels;
    private static final Set<Integer> badTech = new HashSet<>(Arrays.asList(new Integer[]{136,182,301,316,519,527}));
    
    private static final String selectPatentData = "SELECT p.pub_doc_number, abstract, invention_title, description, array_to_string(array_agg(class||' '||subclass), ' ') FROM patent_grant_uspto_classification as q join patent_grant as p on (p.pub_doc_number=q.pub_doc_number) WHERE p.pub_doc_number=ANY(?) AND q.pub_doc_number=ANY(?) group by p.pub_doc_number order by p.pub_doc_number";
    private static final int COLUMNS_OF_TEXT = 4;
    private static final int SEED = 123;
    private static final double THRESHOLD = 0.67;
    private Random rand;
    private boolean testing;
    private List<String> labels;
    private ResultSet latestResults;


    public DatabaseIterator(boolean isTesting) throws SQLException {
        this.testing=isTesting;
        this.rand = new Random(SEED);
        setupMainConn();
        setupCompDBConn();
        setupSeedConn();
        setupPatentToTechnologyHash();
        cursor=0;
        System.out.println("Loading data...");
        latestResults = getPatentData();
    }

    public List<String> getLabels() {
        return labels;
    }

    public List<String> getCurrentLabels(){
        return currentLabels;
    }

    public int numPatents() {
        return numPatents;
    }

    public int numTechnologies() {
        return numTechnologies;
    }

    public Set<String> getPatents() {
        return patentToTechnologyHash.keySet();
    }

    public void setupPatentToTechnologyHash() throws SQLException {
        numPatents=0;
        Set<String> technologySet = new HashSet<>();
        patentToTechnologyHash = new HashMap<>();
        PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct t.id) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id)  WHERE inactive='f' AND asset_count < 25 AND r.deal_id IS NOT NULL AND t.name is not null AND t.id!=ANY(?) GROUP BY r.deal_id");
        ps.setArray(1, compDBConn.createArrayOf("INT",badTech.toArray()));
        ResultSet rs = ps.executeQuery();
        Set<String> offLimits = new HashSet<>();

        while(rs.next()) {
            List<String> technologies = new ArrayList<String>();
            boolean valid = true;
            for(Integer tech : (Integer[])rs.getArray(1).getArray()) {
                if(badTech.contains(tech)) { valid=false; break;}
            	technologies.add(tech.toString());
            }
            if(!valid)continue;

            Array reelFrames = rs.getArray(2);
            technologySet.addAll(technologies);
            PreparedStatement ps2 = seedConn.prepareStatement("SELECT DISTINCT doc_number FROM patent_assignment_property_document WHERE (doc_kind='B1' OR doc_kind='B2') AND doc_number IS NOT NULL AND assignment_reel_frame=ANY(?)");
            ps2.setArray(1, reelFrames);
            ps2.setFetchSize(10);
            ResultSet rs2 = ps2.executeQuery();
            // Collect patent numbers
            while(rs2.next()) {
                double threshold = rand.nextGaussian();
                String str = rs2.getString(1);
                if(threshold>THRESHOLD && !testing) {
                    offLimits.add(str);
                    continue;
                } else if (threshold<=THRESHOLD && testing) {
                    offLimits.add(str);
                    continue;
                } else if (offLimits.contains(str)) {
                    continue;
                }
                if(str!=null) {
                    patentToTechnologyHash.put(str, technologies);
                    numPatents++;
                }
            }
            rs2.close();
            ps2.close();
        }
        rs.close();
        ps.close();
        numTechnologies=technologySet.size();
        labels = new LinkedList<>(technologySet);
    }

    public void setupMainConn() throws SQLException {
        System.out.println("Setting up main connection...");
        mainConn = DriverManager.getConnection(patentDBUrl);
        mainConn.setAutoCommit(false);
    }

    public void setupSeedConn() throws SQLException {
        seedConn = DriverManager.getConnection(patentDBUrl);
        seedConn.setAutoCommit(false);
    }

    public void setupCompDBConn() throws SQLException {
        compDBConn = DriverManager.getConnection(compDBUrl);
    }


    public ResultSet getPatentData() {
        try {
            PreparedStatement ps = mainConn.prepareStatement(selectPatentData);
            Array pubNums = mainConn.createArrayOf("VARCHAR",patentToTechnologyHash.keySet().toArray());
            ps.setArray(1, pubNums);
            ps.setArray(2, pubNums);
            ps.setFetchSize(5);
            return ps.executeQuery();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean hasNextDocument() {
        try {
            if(latestResults.isAfterLast()) return false;
            if(latestResults.isLast() && cursor >= (COLUMNS_OF_TEXT-1)) return false;

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean hasNextDocuments() {
        try {
            if(latestResults.isAfterLast()) return false;
            if(latestResults.isLast()) return false;

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public LabelledDocument nextDocument() {
        String toReturn = null;
        if(cursor==0) {
            try {
                latestResults.next();
                currentLabels = patentToTechnologyHash.get(latestResults.getString(1));
                currentPatent = latestResults.getString(1);
            } catch (SQLException sql) { sql.printStackTrace(); }
        }
        try {
            toReturn = latestResults.getString(cursor + 2);
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
        cursor = (cursor+1) % COLUMNS_OF_TEXT;
        if(toReturn==null && hasNextDocument()) return nextDocument();
        if(toReturn==null) toReturn="";

        return setupDocument(toReturn, currentLabels.get(Math.abs(rand.nextInt())%currentLabels.size()));

    }

    public LabelledDocument setupDocument(String input, String label) {
        LabelledDocument doc =new LabelledDocument();
        doc.setContent(input);
        doc.setLabel(label);
        return doc;
    }

    public List<LabelledDocument> nextDocuments() {
        try {
            latestResults.next();
            currentLabels = patentToTechnologyHash.get(latestResults.getString(1));
            currentPatent = latestResults.getString(1);
            List<LabelledDocument> toReturn = new ArrayList<>();
            for(int i = 0; i < COLUMNS_OF_TEXT; i++) {
                try {
                    toReturn.add(setupDocument(latestResults.getString(i+2),latestResults.getString(1)));
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            return toReturn;

        } catch (SQLException sql) {
            sql.printStackTrace();
            return null;
        }

    }

    @Override
    public void reset() {
        try {latestResults.close(); } catch(Exception e) {e.printStackTrace();}
        cursor=0;
        latestResults = getPatentData();
    }

    @Override
    public LabelsSource getLabelsSource() {
        return new LabelsSource() {
			private static final long serialVersionUID = 1L;

			@Override
            public List<String> getLabels() {
                return labels;
            }
        };
    }

	public String getCurrentPatentNumber() {
		// TODO Auto-generated method stub
		return currentPatent;
	}


}
