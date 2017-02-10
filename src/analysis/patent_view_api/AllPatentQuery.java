package analysis.patent_view_api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.StringJoiner;

/**
 * Created by Evan on 2/5/2017.
 */
public class AllPatentQuery implements Query {
    private String query;
    public AllPatentQuery(Collection<String> patents, int page) {
        StringJoiner patentsOr = new StringJoiner("\",\"","[\"","\"]");
        patents.forEach(classCode->patentsOr.add(classCode));
        query="q={\"_and\":[{\"patent_number\":"+patentsOr.toString()+"},{\"_gte\":{\"patent_date\":\"" + LocalDate.now().minusYears(20).toString() + "\"}}]}&f=[\"patent_number\",\"assignee_organization\",\"cpc_subgroup_id\"]&o={\"page\":"+page+",\"per_page\":1000}";
    }
    public String toString() {
        return query;
    }
}