package seeding;

import org.deeplearning4j.text.documentiterator.LabelledDocument;

/**
 * Created by ehallmark on 7/12/16.
 */
public class PatentDocument extends LabelledDocument {
    private String type;

    public PatentDocument(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
