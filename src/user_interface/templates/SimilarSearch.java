package user_interface.templates;

import seeding.Constants;
import user_interface.server.SimilarPatentServer;

import java.util.*;

/**
 * Created by Evan on 6/24/2017.
 */
public class SimilarSearch extends FormTemplate {

    public SimilarSearch() {
        super(Constants.SIMILAR_SEARCH, null, null, null);
    }

    @Override
    public List<FormTemplate> nestedForms() {
        return Arrays.asList(new SimilarAssetSearch(), new SimilarAssigneeSearch());
    }
}
