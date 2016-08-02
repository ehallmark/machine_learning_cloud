package server;

import tools.PatentList;

import java.util.List;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse extends ServerResponse<List<PatentList>> {
    public List<PatentList> results;
    public PatentResponse(List<PatentList> patents, String query) {
        super(patents,query);
    }
}
