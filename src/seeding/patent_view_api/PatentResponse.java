package seeding.patent_view_api;

import java.util.List;

/**
 * Created by Evan on 2/5/2017.
 */
public class PatentResponse {
    private List<Patent> patents;
    private Integer count;
    private Integer total_patent_count;

    public List<Patent> getPatents() {
        return patents;
    }

    public int getTotalPatentCount() {
        return total_patent_count;
    }
}
