package analysis.patent_view_api;

import java.util.Collection;

/**
 * Created by Evan on 2/5/2017.
 */
public class CPC {
    private String cpc_subgroup_id;
    private String cpc_subgroup_title;
    private String cpc_subsection_id;
    private String cpc_subsection_title;
    public String getSubgroup() {
        return cpc_subgroup_id;
    }
    public String getSubgroupTitle() {
        return cpc_subgroup_title;
    }
    public String getSubsection() {
        return cpc_subsection_id;
    }
    public String getSubsectionTitle() {
        return cpc_subsection_title;
    }

}
