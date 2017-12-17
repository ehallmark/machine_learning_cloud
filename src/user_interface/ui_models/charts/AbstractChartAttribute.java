package user_interface.ui_models.charts;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import spark.Request;
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
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.option;
import static j2html.TagCreator.select;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AbstractChartAttribute extends NestedAttribute implements DependentAttribute<AbstractChartAttribute> {
    protected Collection<String> searchTypes;
    @Getter
    protected List<String> attrNames;
    @Getter
    protected String name;
    public AbstractChartAttribute(Collection<AbstractAttribute> attributes, String name) {
        super(attributes);
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

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attrNames = SimilarPatentServer.extractArray(params, getName());
        attrNames = attrNames.stream().flatMap(name->{
            List<String> children = SimilarPatentServer.extractArray(params, name.replace(".","")+"[]");
            if(children.size()>0) {
                return children.stream().map(child->child.substring(child.indexOf(".")));
            } else {
                return Stream.of(name.substring(name.indexOf(".")));
            }
        }).collect(Collectors.toList());
        System.out.println("Search attrs for "+getName()+": "+attrNames);
        searchTypes = SimilarPatentServer.extractArray(params, Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
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
