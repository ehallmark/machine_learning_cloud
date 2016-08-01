package server;

import tools.PatentList;

import java.util.List;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse {
    public List<PatentList> results;
    public PatentResponse(List<PatentList> patents) {
        this.results=patents;
    }
}
