package analysis.patent_view_api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.StringJoiner;

/**
 * Created by Evan on 2/5/2017.
 */
public class PatentKeywordQuery implements Query {
    private String query;
    public PatentKeywordQuery(Collection<String> keywords, int page) {
        StringJoiner keywordOr = new StringJoiner(",");
        for(String keyword: keywords) {
            keywordOr.add("{\"_text_all\":{\"patent_title\":\""+keyword+"\"}},{\"_text_all\":{\"patent_abstract\":\""+keyword+"\"}}");
        }
        query="q={\"_and\":[{\"_or\":["+keywordOr.toString()+"]},{\"_gte\":{\"patent_date\":\""+ LocalDate.now().minusYears(20).toString()+"\"}}]}&f=[\"patent_number\",\"assignee_organization\",\"patent_title\",\"cpc_subgroup_id\"]&o={\"page\":"+page+",\"per_page\":1000}";
    }
    public String toString() {
        return query;
    }
}