package server;

import java.util.List;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse {
    public List<AbstractPatent> results;
    public PatentResponse(List<AbstractPatent> patents) {
        this.results=patents;
    }
}
