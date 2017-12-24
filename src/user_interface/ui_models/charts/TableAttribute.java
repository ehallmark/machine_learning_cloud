package user_interface.ui_models.charts;

import j2html.tags.Tag;
import lombok.Getter;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.tables.TableResponse;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class TableAttribute extends AbstractChartAttribute {
    @Getter
    protected String collectByAttrName;
    public TableAttribute(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttributes, String name) {
        super(attributes,groupedByAttributes,name,false, false);
    }

    public abstract List<TableResponse> createTables(PortfolioList portfolioList);

    @Override
    public List<String> getInputIds() {
        return Arrays.asList(getGroupByChartFieldName(null),getId());
    }

    public static Tag getTable(TableResponse response, String type, int tableIdx) {
        return div().attr("style", "width: 80%; margin-left: 10%; margin-bottom: 30px;").withClass(type).withId("table-" + tableIdx).with(
                h5(response.title),br(),
                form().withMethod("post").withTarget("_blank").withAction(SimilarPatentServer.DOWNLOAD_URL).with(
                        input().withType("hidden").withName("tableId").withValue(String.valueOf(tableIdx)),
                        button("Download to Excel").withType("submit").withClass("btn btn-secondary div-button").attr("style","width: 40%; margin-bottom: 20px;")
                ),
                table().withClass("table table-striped").withId(type+"-table-"+tableIdx+"table").attr("style","margin-left: 3%; margin-right: 3%; width: 94%;").with(
                        thead().with(
                                tr().with(
                                        response.headers.stream().map(header -> th(SimilarPatentServer.fullHumanAttributeFor(header)).attr("data-dynatable-column", header)).collect(Collectors.toList())
                                )
                        ), tbody()
                )
        )   ;
    }
}
