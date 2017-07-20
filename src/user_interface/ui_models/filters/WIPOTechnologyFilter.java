package user_interface.ui_models.filters;

import models.classification_models.WIPOHelper;
import j2html.tags.Tag;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/17/2017.
 */
public class WIPOTechnologyFilter extends AbstractTechnologyFilter {
    public WIPOTechnologyFilter() {
        super(WIPOHelper.getOrderedClassifications(), Constants.WIPO_TECHNOLOGY, SimilarPatentServer.WIPO_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD);
    }
}
