package tools;

import server.PatentResponse;
import server.ServerResponse;

import java.util.List;
import java.util.StringJoiner;

/**
 * Created by ehallmark on 8/2/16.
 */
public class CSVHelper {
    public static String to_csv(ServerResponse response) {
        if(!(response instanceof PatentResponse)) return response.results.toString();

        // List
        List<PatentList> patentLists = ((PatentResponse)response).results;
        patentLists.sort((PatentList o1, PatentList o2)->o1.getBySimilarityTo().compareTo(o2.getBySimilarityTo()));

        StringJoiner rows = new StringJoiner(System.getProperty("line.separator"));
        rows.add("Similar Patents to "+((PatentResponse)response).query);
        rows.add("").add("");
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
