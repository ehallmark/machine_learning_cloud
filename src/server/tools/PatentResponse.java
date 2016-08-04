package server.tools;

import tools.PatentList;

import java.util.List;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse extends ServerResponse {
    public PatentResponse(List<PatentList> patents, String query) {
        super(query,"Success",patents);
    }
}
