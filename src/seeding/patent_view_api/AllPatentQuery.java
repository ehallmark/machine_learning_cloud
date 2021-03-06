package seeding.patent_view_api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.StringJoiner;

/**
 * Created by Evan on 2/5/2017.
 */
public class AllPatentQuery implements Query {
    private String query;
    public AllPatentQuery(Collection<String> items, int page) {
        StringJoiner patentsOr = new StringJoiner("\",\"","[\"","\"]");
        items.forEach(patent-> {
            patentsOr.add(patent);
        });
        String clause;
        StringJoiner whereClause = new StringJoiner(",");
        whereClause.add("{\"patent_number\":"+patentsOr.toString()+"}");
        clause = whereClause.toString();
        query="q={\"_and\":["+clause+",{\"_gte\":{\"patent_date\":\"" + LocalDate.now().minusYears(20).toString() + "\"}}]}&f=[\"patent_number\",\"assignee_organization\",\"patent_title\",\"cpc_subgroup_id\",\"patent_date\",\"patent_abstract\"]&o={\"page\":"+page+",\"per_page\":1000}";
    }
    public String toString() {
        return query;
    }

}