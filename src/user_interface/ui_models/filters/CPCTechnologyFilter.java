package user_interface.ui_models.filters;

import models.classification_models.WIPOHelper;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;

import java.util.stream.Collectors;

/**
 * Created by Evan on 6/17/2017.
 */
public class CPCTechnologyFilter extends AbstractTechnologyFilter {
    public CPCTechnologyFilter() {
        super(Database.getClassCodeToClassTitleMap().values().stream().sorted().collect(Collectors.toList()), Constants.CPC_TECHNOLOGY, SimilarPatentServer.CPC_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD);
    }
}
