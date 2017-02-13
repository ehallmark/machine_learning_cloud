package server;

import analysis.Patent;
import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import j2html.tags.*;
import seeding.Database;
import server.tools.AbstractPatent;
import server.tools.SimpleAjaxMessage;
import server.tools.excel.ExcelHandler;
import server.tools.excel.ExcelWritable;
import tools.ClassCodeHandler;
import tools.PortfolioList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Created by Evan on 2/12/2017.
 */
public class CompanyPortfolioProfileUI {
    private static final String SELECT_COMPANY_NAME_FORM_ID = "select-company-name-form";
    private static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static final List<String> reportTypes = Arrays.asList("Similar Assignee Report","Similar Patent Report","Patent Valuation Report","Technology Tag Report");
    static Tag companyNameForm() {
        return div().with(
                h3("Company Profiler"),
                SimilarPatentServer.expandableDiv("",false,div().with(
                SimilarPatentServer.formScript(SELECT_COMPANY_NAME_FORM_ID, "/company_names", "Search",true),
                form().withId(SELECT_COMPANY_NAME_FORM_ID).with(
                        h3("Company Information"),
                        label("Enter Company Name"),br(),
                        input().withType("text").withName("assignee"),br(),br(),
                        button("Search").withId(SELECT_COMPANY_NAME_FORM_ID+"-button").withType("submit")
                )
            ))
        );
    }

    static Tag generateReportsForm(String assignee) {
        return div().with(form().withId(GENERATE_REPORTS_FORM_ID).attr("onsubmit",  (
                "$('#"+GENERATE_REPORTS_FORM_ID+"-button').attr('disabled',true).text('Generating...');"
                + "var url = '/company_profile_report'; "
                + "$.ajax({"
                + "  type: 'POST',"
                + "  url: url,"
                + "  data: $('#"+GENERATE_REPORTS_FORM_ID+"').serialize(),"
                + "  success: function(data) { "
                + "     $copy = $('#results form');"
                + "     $copy.siblings().remove();"
                + "    $('#results').html($copy[0].outerHTML+'<hr />'+data.message); "
                + "    $('#"+GENERATE_REPORTS_FORM_ID+"-button').attr('disabled',false).text('Generate Report(s)');"
                + "  }"
                + "});"
                + "return false; "
                )).with(
                SimilarPatentServer.expandableDiv("Report Types",div().with(
                        h4("Report Types"),
                        input().withType("hidden").withName("assignee").withValue(assignee),
                        div().with(reportTypes.stream().sorted().map(type->{
                                    return div().with(
                                            label(type),input().withType("radio").withName("report_type").withValue(type),br()
                                    );
                                }).collect(Collectors.toList())
                        )
                )),br(),
                button("Generate Report(s)").withId(GENERATE_REPORTS_FORM_ID+"-button").withType("submit"))
        );
    }

    static void setupServer() {        // Host my own image asset!
        get("/images/brand.png", (request, response) -> {
            response.type("image/png");

            String pathToImage = "images/brand.png";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            response.status(200);
            return response.body();
        });

        get("/company_profile", (req, res) -> SimilarPatentServer.templateWrapper(res, div().with(companyNameForm(), hr()), SimilarPatentServer.getAndRemoveMessage(req.session())));
        post("/company_names", (req, res) -> {
            res.type("application/json");
            String assigneeStr = req.queryParams("assignee");
            if(assigneeStr==null||assigneeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter a Company"));
            return new Gson().toJson(new SimpleAjaxMessage(div().with(
                    label("Patents found: "+String.valueOf(Database.getAssetCountFor(assigneeStr))),br(),
                    hr(),
                    generateReportsForm(assigneeStr)
            ).render()));
        });

        post("/company_profile_report", (req, res) -> {
            res.type("application/json");
            String assigneeStr = req.queryParams("assignee");
            String reportType = req.queryParams("report_type");
            if(assigneeStr==null||assigneeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter a Company"));
            if(reportType==null||reportType.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter a Report Type"));

            List<String> attributes = Arrays.asList("assignee","totalAssetCount","claimValue","citationValue","technologyValue","claimValue","overallValue");

            boolean includeSubclasses = false;
            boolean allowResultsFromOtherCandidateSet = true;
            boolean searchEntireDatabase = true;
            boolean mergeSearchInput = true;
            String searchType = "assignees";
            PortfolioList.Type portfolioType = PortfolioList.Type.assignees;
            Set<String> patentsToSearchFor = new HashSet<>();
            Set<String> classCodesToSearchFor = new HashSet<>();
            Set<String> assigneesToSearchFor = new HashSet<>();

            // pre data
            Collection<String> patentsToSearchIn = Database.selectPatentNumbersFromAssignee(assigneeStr);
            List<String> customAssigneeList = Arrays.asList(assigneeStr);
            Set<String> labelsToExclude = new HashSet<>();

            SimilarPatentFinder firstFinder = SimilarPatentServer.getFirstPatentFinder(labelsToExclude,customAssigneeList,patentsToSearchIn,new HashSet<>(),searchEntireDatabase,includeSubclasses,allowResultsFromOtherCandidateSet,searchType,patentsToSearchFor,assigneesToSearchFor,classCodesToSearchFor);

            if (firstFinder == null || firstFinder.getPatentList().size() == 0) {
                res.redirect("/company_profile");
                req.session().attribute("message", "Unable to find any results to search in.");
                return null;
            }

            List<SimilarPatentFinder> secondFinders = SimilarPatentServer.getSecondPatentFinder(mergeSearchInput,patentsToSearchFor,assigneesToSearchFor,classCodesToSearchFor);

            if (secondFinders.isEmpty() || secondFinders.stream().collect(Collectors.summingInt(finder -> finder.getPatentList().size())) == 0) {
                res.redirect("/company_profile");
                req.session().attribute("message", "Unable to find any of the search inputs.");
                return null;
            }

            PortfolioList portfolioList = SimilarPatentServer.runPatentFinderModel(reportType, firstFinder, secondFinders, 100, 0.0, labelsToExclude, new HashSet<>(), portfolioType);

            SimilarPatentServer.modelMap.forEach((key,model)->{
                if ((attributes.contains("overallValue")||attributes.contains(key)) && model != null) {
                    SimilarPatentServer.evaluateModel(model,portfolioList.getPortfolio(),key);
                }
            });


            // Handle overall value
            if(attributes.contains("overallValue")) {
                portfolioList.computeAvgValues();
            }

            portfolioList.init();

            return new Gson().toJson(new SimpleAjaxMessage(div().with(
                    h4(reportType+" for "+assigneeStr),
                    tableFromPatentList(portfolioList.getPortfolio(), attributes.stream().map(attr->ExcelWritable.humanAttributeFor(attr)).collect(Collectors.toList()))
            ).render()));
        });
    }

    static Tag tableFromPatentList(List<ExcelWritable> items, List<String> headers) {
        return table().with(
                thead().with(
                        tr().with(
                                headers.stream().map(header->th(header)).collect(Collectors.toList())
                        )
                ),tbody().with(
                        items.stream().sorted().map(item->tr().with(
                                item.getDataAsRow(headers).getCells().stream().map(cell->td(cell.getContent().toString())).collect(Collectors.toList())
                        )).collect(Collectors.toList())
                )

        );
    }

    public static void main(String[] args) throws Exception {
        setupServer();
    }
}
