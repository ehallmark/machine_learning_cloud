package analysis.patent_view_api;

import java.util.List;

/**
 * Created by Evan on 2/5/2017.
 */
public class Patent {
    private List<CPC> cpcs;
    private List<Assignee> assignees;
    private String patent_number;
    private String patent_title;

    public String getInventionTitle() {
        return patent_title;
    }

    public String getPatentNumber() {
        return patent_number;
    }

    public List<Assignee> getAssignees() {
        return assignees;
    }

    public List<CPC> getClassCodes() {
        return cpcs;
    }
}
