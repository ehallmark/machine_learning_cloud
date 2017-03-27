package analysis.patent_view_api;

import seeding.Database;

import java.time.LocalDate;
import java.util.Collection;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Evan on 2/5/2017.
 */
public class MonthlyPatentQuery implements Query {
    private String query;
    public MonthlyPatentQuery(LocalDate startMonth, int page) {
        String startDateStr = startMonth.withDayOfMonth(1).toString();
        String endDateStr = startMonth.withDayOfMonth(1).plusMonths(1).toString();
        query="q={\"_and\":[{\"_gte\":{\"patent_date\":\""+startDateStr+"\"}},{\"_lt\":{\"patent_date\":\""+endDateStr+"\"}}]}&f=[\"patent_number\",\"assignee_organization\",\"assignee_total_num_patents\",\"patent_title\",\"assignee_type\",\"cpc_subsection_id\",\"cpc_subgroup_id\",\"cpc_subsection_title\",\"cpc_subgroup_title\",\"patent_abstract\"]&o={\"page\":"+page+",\"per_page\":1000}";
    }
    public String toString() {
        return query;
    }
}