package analysis.patent_view_api;

import seeding.Database;

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
        StringJoiner assigneesOr = new StringJoiner("\",\"","[\"","\"]");
        items.forEach(patent-> {
            if(Database.isAssignee(patent)) {
                assigneesOr.add(patent);
            } else {
                patentsOr.add(patent);
            }
        });
        query="q={\"_and\":[{\"patent_number\":"+patentsOr.toString()+"},{\"assignee_organization\":"+assigneesOr.toString()+"},{\"_gte\":{\"patent_date\":\"" + LocalDate.now().minusYears(20).toString() + "\"}}]}&f=[\"patent_number\",\"assignee_organization\",\"patent_title\",\"cpc_subgroup_id\",\"patent_date\",\"patent_abstract\"]&o={\"page\":"+page+",\"per_page\":1000}";
    }
    public String toString() {
        return query;
    }
}