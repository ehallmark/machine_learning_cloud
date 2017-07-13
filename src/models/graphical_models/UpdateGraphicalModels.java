package models.graphical_models;

import models.graphical_models.page_rank.PageRankHelper;
import models.graphical_models.page_rank.SimRankHelper;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateGraphicalModels {
    public static void main(String[] args) {
        PageRankHelper.main(args);

        // DEPRECATED
        //SimRankHelper.main(args);
    }
}
