package user_interface.ui_models.charts;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.attributes.DependentAttribute;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.span;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class ChartAttribute extends AbstractAttribute implements DependentAttribute<ChartAttribute> {
    @Getter
    protected List<String> attributes;
    @Getter
    protected String name;
    public ChartAttribute(List<String> attributes, String name) {
        super(Collections.emptyList());
        this.name=name;
        this.attributes=attributes;
    }

    protected static String combineTypesToString(Collection<String> types) {
        if(types.isEmpty()) return "";
        if(types.size()==1) {
            return SimilarPatentServer.humanAttributeFor(types.stream().findAny().get());
        } else {
            return "Assets";
        }
    }

    public abstract List<? extends AbstractChart> create(PortfolioList portfolioList, int i);

    @Override
    public AbstractFilter.FieldType getFieldType() { throw new UnsupportedOperationException("fieldType not defined for charts.");}

    public Tag getDescription() {
        String text = Constants.ATTRIBUTE_DESCRIPTION_MAP.get(getType());
        if(text==null) return span();
        return div().with(
                div().withText(text),
                div().with(
                        getAttributes().stream().map(attr->{
                            return div().withText("The "+SimilarPatentServer.humanAttributeFor(attr).toLowerCase());
                        }).collect(Collectors.toList())
                )
        );
    }


}
