package analysis.patent_view_api;

/**
 * Created by Evan on 2/5/2017.
 */
public class GetAssetsFromAssigneeQuery implements Query {
    private String query;
    public GetAssetsFromAssigneeQuery(String assignee, int page) {
        query="q={\"_begins\":{\"assignee_organization\":\""+assignee+"\"}}&f=[\"patent_number\"]&o={\"page\":"+page+",\"per_page\":1000}";
    }
    public String toString() {
        return query;
    }
}