package seeding;

import java.io.*;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;


/**
 * Created by ehallmark on 6/24/16.
 */
public class DatabaseIterator {

    private String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private static String compDBUrl = "jdbc:postgresql://192.168.1.148/compdb_production?user=postgres&password=&tcpKeepAlive=true";
    private static Connection seedConn;
    private static Connection mainConn;
    private static Connection compDBConn;
    private Map<String,List<String>> patentToTechnologyHash;
    private int numPatents;
    private int numTechnologies;
    private String currentPatent;

    private List<String> currentLabels;
    private static final Set<Integer> badTech = new HashSet<>(Arrays.asList(136,182,301,316,519,527));
    
    private static final String selectPatentData = "SELECT p.pub_doc_number, abstract, invention_title, description, array_agg(distinct class), array_agg(distinct subclass) FROM patent_grant as p join patent_grant_uspto_classification as q on (p.pub_doc_number=q.pub_doc_number) WHERE p.pub_doc_number=ANY(?) AND q.pub_doc_number=ANY(?) AND (abstract IS NOT NULL OR invention_title IS NOT NULL OR description IS NOT NULL) group by p.pub_doc_number order by p.pub_doc_number";
    private static final int COLUMNS_OF_TEXT = 3;
    private static final int COLUMNS_OF_ARRAYS = 2;
    private static final int SEED = 123;
    private static final double THRESHOLD = 0.67;
    private Random rand;
    private boolean testing;
    private ResultSet latestResults;
    private TokenPreProcess preProcessor;

    public DatabaseIterator(boolean isTesting, TokenPreProcess preProcessor) throws SQLException {
        this.testing=isTesting;
        this.rand = new Random(SEED);
        this.preProcessor=preProcessor;
        setupMainConn();
        setupCompDBConn();
        setupSeedConn();
        setupPatentToTechnologyHash();
        System.out.println("Loading data...");
        latestResults = getPatentData();
    }
    
    public Map<Integer, String> initializeTechnologyHash() throws SQLException, IOException, ClassNotFoundException {
        if(!new File(Constants.COMPDB_TECHNOLOGIES_INTEGER_TO_STRING_MAP).isFile()) {
            System.out.println("Loading classifications...");

            HashMap<Integer,String> technologyHash = new HashMap<>();
            PreparedStatement ps = compDBConn.prepareStatement("SELECT DISTINCT id,name FROM technologies WHERE name is not null and char_length(name) > 0 and id!=ANY(?)");
            ps.setArray(1, compDBConn.createArrayOf("INT", badTech.toArray()));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                technologyHash.put(rs.getInt(1), rs.getString(2));
            }
            return technologyHash;
        } else {
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(new File(Constants.COMPDB_TECHNOLOGIES_INTEGER_TO_STRING_MAP))));
            return (Map<Integer, String>)ois.readObject();
        }
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


    public LabelledDocument setupDocument(String input, String label, String type) {
        LabelledDocument doc =new PatentDocument(type);
        doc.setContent(input);
        doc.setLabel(label);
        return doc;
    }

    public List<LabelledDocument> nextDocuments() {
        try {
            latestResults.next();
            currentPatent = latestResults.getString(1);
            currentLabels = patentToTechnologyHash.get(currentPatent);

            List<LabelledDocument> toReturn = new ArrayList<>();
            for(int i = 0; i < COLUMNS_OF_TEXT; i++) {
                String type;
                if(i==0)type=Constants.ABSTRACT;
                else if(i==1)type=Constants.INVENTION_TITLE;
                else if (i==2)type=Constants.DESCRIPTION;
                else throw new RuntimeException("Unknown PatentDocument type!");
                try {
                    if(!type.equals(Constants.INVENTION_TITLE)) {
                        // get sentences
                        StringJoiner sentences = new StringJoiner(System.getProperty("line.separator"));
                        for(String sentence : latestResults.getString(i+2).replaceAll("fig\\.","fig").split("\\.\\s+")) {
                            String toAdd = preProcessor.preProcess(sentence);
                            if(toAdd.split("\\s+").length > 4) sentences.add(toAdd);
                        }
                        if(sentences.length() > 0)toReturn.add(setupDocument(sentences.toString(),currentPatent,type));
                    } else {
                        if(latestResults.getString(i+2).length() >0) toReturn.add(setupDocument(preProcessor.preProcess(latestResults.getString(i+2)),currentPatent,type));
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            for(int i = 0; i < COLUMNS_OF_ARRAYS; i++) {
                String type;
                if(i==0)type=Constants.USPTO_CLASS;
                else if(i==1)type=Constants.USPTO_SUBCLASS;
                else throw new RuntimeException("Unknown PatentDocument type!");
                try {
                    String[] arrayText = (String[])latestResults.getArray(COLUMNS_OF_TEXT+i+2).getArray();
                    if(arrayText!=null) {
                        int j = 0;
                        for(String text : arrayText) {
                            toReturn.add(setupDocument(text,currentPatent,type+"_"+j));
                            j++;
                        }
                    }
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


	public String getCurrentPatentNumber() {
		// TODO Auto-generated method stub
		return currentPatent;
	}


}
