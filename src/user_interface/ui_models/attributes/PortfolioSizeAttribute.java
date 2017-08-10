package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.meta_attributes.AssigneeToAssetMapAttribute;
import user_interface.ui_models.attributes.meta_attributes.MetaComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.meta;

/**
 * Created by ehallmark on 6/15/17.
 */
public class PortfolioSizeAttribute extends ComputableAttribute<Integer> {
    protected static AssigneeToAssetMapAttribute assigneeToAssetMap = new AssigneeToAssetMapAttribute();
    public PortfolioSizeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public Collection<MetaComputableAttribute> getNecessaryMetaAttributes() {
        return Arrays.asList(assigneeToAssetMap);
    }

    @Override
    public Integer attributesFor(Collection<String> portfolioList, int limit) {
        if(portfolioList.isEmpty()) return null;
        String name = portfolioList.stream().findAny().get();
        if(Database.isApplication(name)) {
            return assigneeToAssetMap.applicationDataMap.getOrDefault(name, Collections.emptyList()).size();
        } else {
            return assigneeToAssetMap.patentDataMap.getOrDefault(name, Collections.emptyList()).size();
        }
    }

    @Override
    public Integer handleIncomingData(String name, Map<String, Object> data, boolean isApplication) {
        return null;
    }

    @Override
    public String getAssociation() {
        return Constants.ASSIGNEES;
    }

    public String getAssociatedField() {
        return Constants.ASSIGNEE;
    }


    @Override
    public String getName() {
        return Constants.PORTFOLIO_SIZE;
    }

    @Override
    public String getType() {
        return "integer";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

}

