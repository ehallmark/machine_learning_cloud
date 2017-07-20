package user_interface.ui_models.filters;

import seeding.Constants;
import user_interface.server.SimilarPatentServer;

import java.util.stream.Collectors;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyFilter extends AbstractTechnologyFilter {
    public TechnologyFilter() {
        super(SimilarPatentServer.getTechTagger().getClassifications().stream().sorted().collect(Collectors.toList()), Constants.TECHNOLOGY, SimilarPatentServer.TECHNOLOGIES_TO_FILTER_ARRAY_FIELD);
    }
}
