package user_interface.ui_models.charts;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.span;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AbstractChartAttribute extends AbstractAttribute implements DependentAttribute<AbstractChartAttribute> {
    @Getter
    protected List<AbstractAttribute> attributes;
    ;
    @Getter
    protected List<String> attrNames;
    @Getter
    protected String name;
    public AbstractChartAttribute(List<AbstractAttribute> attributes, String name) {
        super(Collections.emptyList());
        this.attributes=attributes;
        this.name=name;
    }

    protected static String combineTypesToString(Collection<String> types) {
        if(types.isEmpty()) return "";
        if(types.size()==1) {
            return SimilarPatentServer.humanAttributeFor(types.stream().findAny().get());
        } else {
            return "Assets";
        }
    }


    public Tag technologySelect(Function<String,Boolean> userRoleFunction) {
        String styleString = "margin-left: 5%; margin-right: 5%; display: none;";
        String name = getFullName().replace(".","");
        List<AbstractAttribute> applicableAttributes = attributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getRootName())).collect(Collectors.toList());
        return div().with(
                div().with(
                        SimilarPatentServer.technologySelectWithCustomClass(name+(name.endsWith("[]")?"":"[]"),"nested-filter-select", applicableAttributes.stream().map(attr->attr.getFullName()).collect(Collectors.toList()))
                ), div().withClass("nested-form-list").with(
                        applicableAttributes.stream().map(filter->{
                            String collapseId = "collapse-filters-"+filter.getFullName().replaceAll("[\\[\\]]","");
                            return div().attr("style", styleString).with(
                                    SimilarPatentServer.createAttributeElement(filter.getFullName(),null,collapseId,filter.getOptionsTag(userRoleFunction), filter.isNotYetImplemented(), filter.getDescription().render())
                            );
                        }).collect(Collectors.toList())
                )
        );
    }


    @Override
    public AbstractFilter.FieldType getFieldType() { throw new UnsupportedOperationException("fieldType not defined for charts.");}

    public Tag getDescription() {
        String text = Constants.ATTRIBUTE_DESCRIPTION_MAP.get(getType());
        if(text==null) return span();
        return div().with(
                div().withText(text)
        );
    }


}
