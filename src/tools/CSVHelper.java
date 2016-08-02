package tools;

import server.ServerResponse;

import java.util.List;
import java.util.StringJoiner;

/**
 * Created by ehallmark on 8/2/16.
 */
public class CSVHelper {
    public static String to_csv(ServerResponse response) {
        if(response.results==null) return response.message;

        // List
        List<PatentList> patentLists = response.results;

        StringJoiner rows = new StringJoiner(System.getProperty("line.separator"));
        rows.add("Similar Patents to "+response.query.trim());
        rows.add("");
        patentLists.forEach(patentList->{
            rows.add("By Similarity of "+patentList.getBySimilarityTo());
            rows.add("Patent Number,Cosine Similarity");
            patentList.getPatents().forEach(patent->{
                StringJoiner columns = new StringJoiner(",");
                columns.add(patent.getName()).add(String.valueOf(patent.getSimilarity()));
                rows.add(columns.toString());
            });
            rows.add("");
        });

        return rows.toString();
    }
}
