package analysis.patent_view_api;

import seeding.Database;

import java.time.LocalDate;
import java.util.Collection;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Evan on 2/5/2017.
 */
public class AllPatentQuery implements Query {
    private String query;
    public AllPatentQuery(Collection<String> items, int page) {
        StringJoiner patentsOr = new StringJoiner("\",\"","[\"","\"]");
        StringJoiner assigneesOr = new StringJoiner("\",\"","[\"","\"]");
        AtomicBoolean hasPatent = new AtomicBoolean(false);
        AtomicBoolean hasAssignee = new AtomicBoolean(false);
        items.forEach(patent-> {
            if(Database.isAssignee(patent)) {
                assigneesOr.add(patent);
                hasAssignee.set(true);
            } else {
                patentsOr.add(patent);
                hasPatent.set(true);
            }
        });
        String clause;
        StringJoiner whereClause = new StringJoiner(",");
        if(hasPatent.get()) {
            whereClause.add("{\"patent_number\":"+patentsOr.toString()+"}");
        }
        if(hasAssignee.get()) {
            whereClause.add("{\"assignee_organization\":"+assigneesOr.toString()+"}");
        }
        if(hasAssignee.get()&&hasPatent.get()) {
            // need or
            clause = "{\"_and\":["+whereClause.toString()+"]}";
        } else clause = whereClause.toString();
        query="q={\"_and\":["+clause+",{\"_gte\":{\"patent_date\":\"" + LocalDate.now().minusYears(20).toString() + "\"}}]}&f=[\"patent_number\",\"assignee_organization\",\"patent_title\",\"cpc_subgroup_id\",\"patent_date\",\"patent_abstract\"]&o={\"page\":"+page+",\"per_page\":1000}";
    }
    public String toString() {
        return query;
    }
}