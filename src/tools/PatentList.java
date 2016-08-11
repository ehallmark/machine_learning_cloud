package tools;

import server.tools.AbstractPatent;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<AbstractPatent> patents;
    private String similarToPatent;
    public PatentList(List<AbstractPatent> patentList, String similarToPatent) {
        this.patents=patentList;
        this.similarToPatent=similarToPatent;
        Collections.sort(patents);
        Collections.reverse(patents);
    }


    public List<AbstractPatent> getPatents() {
        return patents;
    }

    public String getSimilarToPatent() {
        return similarToPatent;
    }
}
