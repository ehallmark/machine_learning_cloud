package user_interface.ui_models.filters;

import seeding.Constants;
import user_interface.server.SimilarPatentServer;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/17/2017.
 */
public class EntityTypeFilter extends AbstractTechnologyFilter {
    public EntityTypeFilter() {
        super(Arrays.asList("Micro","Small","Large"), Constants.ASSIGNEE_ENTITY_TYPE, Constants.ENTITY_TYPE_FILTER_ARRAY);
    }
}
