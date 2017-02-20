package analysis.patent_view_api;

import java.time.LocalDate;
import java.util.List;

/**
 * Created by Evan on 2/5/2017.
 */
public class Patent {
    private List<CPC> cpcs;
    private List<Assignee> assignees;
    private String patent_number;
    private String patent_title;
    private String patent_date;
    private String patent_abstract;

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

    public String getAbstract() { return patent_abstract; }

    public LocalDate getPubDate() {
        try {
            return LocalDate.parse(patent_date);
        } catch(Exception e) {
            return null;
        }
    }

    @Override
    public int hashCode() {
        return patent_number.hashCode();
    }
    @Override
    public boolean equals(Object other) {
        return other.equals(patent_number);
    }
}
