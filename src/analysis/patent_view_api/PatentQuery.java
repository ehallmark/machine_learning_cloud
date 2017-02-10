package analysis.patent_view_api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.StringJoiner;

/**
 * Created by Evan on 2/5/2017.
 */
public class PatentQuery implements Query {
    private String query;
    public PatentQuery(Collection<String> classCodes, int page) {
        StringJoiner classCodeOr = new StringJoiner("\",\"","[\"","\"]");
        classCodes.forEach(classCode->classCodeOr.add(classCode));
        query="q={\"_and\":[{\"cpc_subgroup_id\":"+classCodeOr.toString()+"},{\"_gte\":{\"patent_date\":\"" + LocalDate.now().minusYears(20).toString() + "\"}}]}&f=[\"patent_number\",\"assignee_organization\",\"cpc_subgroup_id\"]&o={\"page\":"+page+",\"per_page\":1000}";
    }
    public String toString() {
        return query;
    }
}