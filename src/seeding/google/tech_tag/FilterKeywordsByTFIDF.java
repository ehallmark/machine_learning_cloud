package seeding.google.tech_tag;

import graphical_modeling.util.Pair;
import seeding.Constants;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FilterKeywordsByTFIDF {
    public static void main(String[] args) throws Exception {

        Connection conn = Database.getConn();

        AtomicLong numDocuments = new AtomicLong(0);
        {
            PreparedStatement ps = conn.prepareStatement("select count(*) from big_query_keywords_all");
            ResultSet rs = ps.executeQuery();
            if(rs.next()) {
                numDocuments.set(rs.getInt(1));
            } else {
                System.out.println("Unable to get document counts using query: "+ps.toString());
                System.exit(1);
            }
        }
        System.out.println("Num documents: "+numDocuments.get());
        if(numDocuments.get()==0) {
            throw new RuntimeException("Must have a positive number of documents...");
        }

        Map<String,Pair<Integer, Integer>> wordData = new HashMap<>();
        System.out.println("Starting to read word data...");
        {
            PreparedStatement ps = conn.prepareStatement("select keyword,num_words,doc_count from big_query_keyword_count_helper");
            ps.setFetchSize(100);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String word = rs.getString(1);
                if (!Constants.CLAIM_STOP_WORD_SET.contains(word)) {
                    wordData.put(rs.getString(1), new Pair<>(rs.getInt(2), rs.getInt(3)));
                }
            }
            rs.close();
            ps.close();
        }
        System.out.println("Finished reading word data... Num words: "+wordData.size());

        Connection insertConn = Database.newSeedConn();
        PreparedStatement insertPs = insertConn.prepareStatement("insert into big_query_keywords_tfidf (family_id, keywords) values (?,?) on conflict (family_id) do update set keywords=excluded.keywords");
        PreparedStatement ps = conn.prepareStatement("select p.family_id, p.keywords from big_query_keywords_all as p full outer join big_query_keywords_tfidf as t on (t.family_id=p.family_id) where t.family_id is null");
        ps.setFetchSize(10);
        ResultSet rs = ps.executeQuery();
        System.out.println("Starting to ingest...");
        int seen = 0;
        while(rs.next()) {
            String familyId = rs.getString(1);
            String[] text = (String[])rs.getArray(2).getArray();
            Map<String,Long> countMap = Stream.of(text).collect(Collectors.groupingBy(word->word, Collectors.counting()));
            text = handleTfidf(numDocuments.get(), countMap, wordData);
            if(text.length > 0) {
                insertPs.setString(1, familyId);
                insertPs.setArray(2, insertConn.createArrayOf("varchar", text));
                insertPs.executeUpdate();
            }
            if(seen%10000==9999) {
                System.out.println("Ingested: "+seen);
                insertConn.commit();
            }
            seen++;
        }

        insertConn.commit();
    }


    private static String[] handleTfidf(double numDocuments, Map<String,Long> countMap, Map<String,Pair<Integer,Integer>> wordData) {
        final int tfidfLimit = Math.min(10, countMap.size()/4);
        return countMap.entrySet().stream().filter(e->wordData.containsKey(e.getKey())).map(e->{
            Pair<Integer,Integer> p = wordData.get(e.getKey());
            double tfidf = e.getValue().doubleValue() * Math.log(p._1) * Math.log(numDocuments/p._2);
            return new Pair<>(e.getKey(), tfidf);
        }).sorted((p1,p2)->p2._2.compareTo(p1._2))
                .limit(tfidfLimit)
                .map(p->p._1).toArray(s->new String[s]);
    }
}
