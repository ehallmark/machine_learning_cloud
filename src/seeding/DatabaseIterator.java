package seeding;

import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;

import java.sql.*;
import java.util.*;

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

    private List<String> currentLabels;
    private static final Set<String> badTechnologies = new HashSet<String>(Arrays.asList(new String[]{"large","empty","manual review","large portfolio","various technologies","huge portfolio"}));
    private static final String selectPatentAbstractWords = "SELECT regexp_replace(lower(regexp_replace(abstract, '[-_]' , ' ', 'g')), '[^a-z ]', '', 'g') as text FROM patent_grant WHERE pub_doc_number=?";
    private static final String selectPatentDescriptionWords = "SELECT  as text FROM patent_grant WHERE pub_doc_number=?";
    private static final String selectPatentTitleWords = "SELECT regexp_replace(lower(regexp_replace(invention_title, '[-_]' , ' ', 'g')), '[^a-z ]', '', 'g') as text FROM patent_grant WHERE pub_doc_number=?";
    private static final String selectPatentData = "SELECT p.pub_doc_number, abstract, invention_title, array_to_string(array_agg(class||' '||subclass), '>><<'), array_to_string(array_agg(patent_cited_invention_title), '<<>>'), array_to_string(array_agg(patent_cited_abstract),'<<>>') FROM patent_grant_uspto_classification as q join patent_grant as p on (p.pub_doc_number=q.pub_doc_number) join patent_grant_citation as r on (p.pub_doc_number=r.pub_doc_number) WHERE r.pub_doc_number=ANY(?) AND p.pub_doc_number=ANY(?) AND q.pub_doc_number=ANY(?) group by p.pub_doc_number order by p.pub_doc_number";
    private static final int COLUMNS_OF_TEXT = 5;
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
        PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct name) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id)  WHERE inactive='f' AND asset_count < 25 AND r.deal_id IS NOT NULL AND t.name is not null AND lower(t.name)!=ANY(?) GROUP BY r.deal_id");
        ps.setArray(1, compDBConn.createArrayOf("varchar",badTechnologies.toArray()));
        ResultSet rs = ps.executeQuery();
        Set<String> offLimits = new HashSet<>();

        while(rs.next()) {
            List<String> technologies = Arrays.asList((String[])rs.getArray(1).getArray());
            if(technologies==null||technologies.isEmpty()) continue;
            boolean valid = true;
            // validation check
            for(String t : technologies) {
                if(badTechnologies.contains(t.toLowerCase())) valid=false;
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


    public String getPatentAbstractWords(String patent) {
        try {
            PreparedStatement ps = mainConn.prepareStatement(selectPatentAbstractWords);
            ps.setString(1, patent);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        return null;
    }


    public String getPatentDescriptionWords(String patent) {
        try {
            PreparedStatement ps = mainConn.prepareStatement(selectPatentDescriptionWords);
            ps.setString(1, patent);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        return null;
    }

    public String getPatentTitleWords(String patent) {
        try {
            PreparedStatement ps = mainConn.prepareStatement(selectPatentTitleWords);
            ps.setString(1, patent);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        return null;
    }

    public ResultSet getPatentData() {
        try {
            PreparedStatement ps = mainConn.prepareStatement(selectPatentData);
            Array pubNums = mainConn.createArrayOf("VARCHAR",patentToTechnologyHash.keySet().toArray());
            ps.setArray(1, pubNums);
            ps.setArray(2, pubNums);
            ps.setArray(3, pubNums);
            ps.setFetchSize(10);
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
        if(toReturn.contains("<<>>")) {
            String[] contents = toReturn.split("<<>>");
            toReturn = contents[Math.abs(rand.nextInt())%contents.length];
        }
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
            @Override
            public List<String> getLabels() {
                return labels;
            }
        };
    }


}
