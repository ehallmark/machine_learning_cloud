package server.tools;

import tools.PatentList;

import java.util.List;

/**
 * Created by ehallmark on 8/2/16.
 */
public class ServerResponse {
    public List<PatentList> results;
    public String query;
    public String message;
    public ServerResponse(String query, String message, List<PatentList> results) {
        this.results=results;
        this.query=query;
        this.message=message;
    }
}
