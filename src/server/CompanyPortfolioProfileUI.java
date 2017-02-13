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
    private static final List<String> reportTypes = Arrays.asList("Portfolio Valuation","Representative Patents","Similar Patent Finder", "Similar Company Finder","Portfolio Technology Tagging");
    private static final Map<String,List<String>> attributesMap;
    static {
        attributesMap=new HashMap<>();
        List<String> valueAttrs = Arrays.asList("name","assignee","title","citationValue","technologyValue","assigneeValue","marketValue","claimValue","overallValue");
        attributesMap.put("Portfolio Valuation",valueAttrs);
        List<String> similarPatentAttrs = Arrays.asList("name","similarity","assignee","title");
        attributesMap.put("Representative Patents",similarPatentAttrs);
        attributesMap.put("Similar Patent Finder",similarPatentAttrs);
        List<String> companyAttrs = Arrays.asList("assignee","totalAssetCount","similarity","relevantAssets");
        attributesMap.put("Similar Company Finder",companyAttrs);
    }
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

            List<String> attributes = attributesMap.get(reportType);
            if(attributes==null) return new Gson().toJson(new SimpleAjaxMessage("Please enter a valid Report Type: "+reportType+" not in ["+String.join("; ",reportTypes)+"]"));

            SimilarPatentFinder firstFinder;
            boolean includeSubclasses = false;
            boolean allowResultsFromOtherCandidateSet;
            boolean searchEntireDatabase;
            boolean mergeSearchInput;
            String searchType;
            PortfolioList.Type portfolioType;
            Set<String> patentsToSearchFor;
            Set<String> classCodesToSearchFor;
            Set<String> assigneesToSearchFor;
            boolean useSimilarPatentFinders;

            // pre data
            Collection<String> patentsToSearchIn;
            List<String> customAssigneeList;
            Set<String> labelsToExclude;
            switch(reportType) {
                case "Portfolio Valuation": {
                    searchEntireDatabase=false;
                    useSimilarPatentFinders=false;
                    patentsToSearchIn = Database.selectPatentNumbersFromAssignee(assigneeStr);
                    customAssigneeList = Collections.emptyList();
                    assigneesToSearchFor=null;
                    patentsToSearchFor=null;
                    classCodesToSearchFor=null;
                    portfolioType= PortfolioList.Type.patents;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=true;
                    searchType="patents";
                    break;
                }
                case "Representative Patents": {
                    searchEntireDatabase=false;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = Database.selectPatentNumbersFromAssignee(assigneeStr);
                    customAssigneeList = Collections.emptyList();
                    assigneesToSearchFor=new HashSet<>(Arrays.asList(assigneeStr));
                    patentsToSearchFor=null;
                    classCodesToSearchFor=null;
                    portfolioType= PortfolioList.Type.patents;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=true;
                    searchType="patents";
                    break;
                }
                case "Similar Patent Finder": {
                    searchEntireDatabase=true;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = Database.selectPatentNumbersFromAssignee(assigneeStr);
                    customAssigneeList = Arrays.asList(assigneeStr);
                    assigneesToSearchFor=null;
                    patentsToSearchFor=null;
                    classCodesToSearchFor=null;
                    portfolioType= PortfolioList.Type.patents;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=false;
                    searchType="patents";
                    break;
                }
                case "Similar Company Finder": {
                    searchEntireDatabase=true;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = Database.selectPatentNumbersFromAssignee(assigneeStr);
                    customAssigneeList = Arrays.asList(assigneeStr);
                    assigneesToSearchFor=null;
                    patentsToSearchFor=null;
                    classCodesToSearchFor=null;
                    portfolioType= PortfolioList.Type.assignees;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=false;
                    searchType="assignees";
                    break;
                }
                /*case "Portfolio Technology Tagging": {
                    // special model
                    break;
                }*/
                default: {
                    return new Gson().toJson(new SimpleAjaxMessage("Report option not yet implemented"));
                }
            }

            System.out.println("Starting to retrieve portfolio list...");
            PortfolioList portfolioList;

            if(useSimilarPatentFinders) {
                System.out.println("Using similar patent finders");
                firstFinder = SimilarPatentServer.getFirstPatentFinder(labelsToExclude, customAssigneeList, patentsToSearchIn, new HashSet<>(), searchEntireDatabase, includeSubclasses, allowResultsFromOtherCandidateSet, searchType, patentsToSearchFor, assigneesToSearchFor, classCodesToSearchFor);

                if (firstFinder == null || firstFinder.getPatentList().size() == 0) {
                    res.redirect("/company_profile");
                    req.session().attribute("message", "Unable to find any results to search in.");
                    return null;
                }

                List<SimilarPatentFinder> secondFinders = SimilarPatentServer.getSecondPatentFinder(mergeSearchInput, patentsToSearchFor, assigneesToSearchFor, classCodesToSearchFor);

                if (secondFinders.isEmpty() || secondFinders.stream().collect(Collectors.summingInt(finder -> finder.getPatentList().size())) == 0) {
                    res.redirect("/company_profile");
                    req.session().attribute("message", "Unable to find any of the search inputs.");
                    return null;
                }

                portfolioList = SimilarPatentServer.runPatentFinderModel(reportType, firstFinder, secondFinders, 100, 0.0, labelsToExclude, new HashSet<>(), portfolioType);

            } else {
                System.out.println("Using abstract portfolio type");
                Set<String> toSearchIn = new HashSet<>();
                switch(portfolioType) {
                    case assignees: {
                        customAssigneeList.forEach(assignee->toSearchIn.addAll(Database.possibleNamesForAssignee(assignee)));
                        break;
                    } case patents: {
                        toSearchIn.addAll(patentsToSearchIn);
                        break;
                    }case class_codes: {
                        break;
                    }
                }

                System.out.println("Starting building portfolio list");
                portfolioList = PortfolioList.abstractPorfolioList(toSearchIn, portfolioType);
                System.out.println("Finished building portfolio list");
            }


            System.out.println("Starting values");
            SimilarPatentServer.modelMap.forEach((key,model)->{
                if ((attributes.contains("overallValue")||attributes.contains(key)) && model != null) {
                    SimilarPatentServer.evaluateModel(model,portfolioList.getPortfolio(),key);
                }
            });


            // Handle overall value
            System.out.println("Starting overall value");
            if(attributes.contains("overallValue")) {
                portfolioList.computeAvgValues();
            }
            System.out.println("Finished overall value");

            portfolioList.init();

            System.out.println("Finished initializing portfolio");

            try {
            return new Gson().toJson(new SimpleAjaxMessage(div().with(
                    h4(reportType+" for "+assigneeStr),
                    tableFromPatentList(portfolioList.getPortfolio(), attributes)
            ).render()));
            } catch(Exception e) {
                System.out.println("Failed to create table from patentlist");
                e.printStackTrace();
                return null;
            }
        });
    }

    static Tag tableFromPatentList(List<ExcelWritable> items, List<String> attributes) {
        return table().with(
                thead().with(
                        tr().with(
                                attributes.stream().map(attr->th(ExcelWritable.humanAttributeFor(attr))).collect(Collectors.toList())
                        )
                ),tbody().with(
                        items.stream().sorted().map(item->tr().with(
                                item.getDataAsRow(attributes).getCells().stream().map(cell->cell==null?td(""):td(cell.getContent().toString())).collect(Collectors.toList())
                        )).collect(Collectors.toList())
                )

        );
    }

    public static void main(String[] args) throws Exception {
        SimilarPatentServer.loadValueModels();
        setupServer();
    }
}
