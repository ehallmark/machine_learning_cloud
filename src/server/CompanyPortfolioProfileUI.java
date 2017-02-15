package server;

import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.options.AxisType;
import j2html.tags.*;
import seeding.Database;
import server.highcharts.*;
import server.tools.AjaxChartMessage;
import server.tools.BackButtonHandler;
import server.tools.SimpleAjaxMessage;
import server.tools.excel.ExcelWritable;
import spark.Request;
import spark.Spark;
import tools.PortfolioList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Created by Evan on 2/12/2017.
 */
public class CompanyPortfolioProfileUI {
    private static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static final List<String> reportTypes = Arrays.asList("Company Valuation","Recent Activity Timeline","Representative Patents","Similar Patent Finder", "Similar Company Finder","Portfolio Technology Tagging");
    private static final Map<String,List<String>> attributesMap;
    static {
        attributesMap=new HashMap<>();
        List<String> valueAttrs = Arrays.asList("name","assignee","title","citationValue","technologyValue","assigneeValue","marketValue","claimValue","overallValue");
        attributesMap.put("Company Valuation",valueAttrs);
        List<String> similarPatentAttrs = Arrays.asList("name","similarity","assignee","title");
        attributesMap.put("Representative Patents",similarPatentAttrs);
        attributesMap.put("Similar Patent Finder",similarPatentAttrs);
        List<String> companyAttrs = Arrays.asList("assignee","totalAssetCount","similarity","relevantAssets");
        attributesMap.put("Similar Company Finder",companyAttrs);
    }

    static Tag generateReportsForm() {
        AtomicBoolean isFirst = new AtomicBoolean(true);
        return div().with(form().withId(GENERATE_REPORTS_FORM_ID).attr("onsubmit",  (
                "$('#"+GENERATE_REPORTS_FORM_ID+"-button').attr('disabled',true).text('Generating...');"
                + "var url = '/company_profile_report'; "
                + "$.ajax({"
                + "  type: 'POST',"
                + "  url: url,"
                + "  data: $('#"+GENERATE_REPORTS_FORM_ID+"').serialize(),"
                + "  success: function(data) { "
                + "    $copy = $('#results form');"
                + "    $copy.siblings().remove();"
                + "    $('#results').html($copy[0].outerHTML+'<hr />'+data.message); "
                + "    $('#"+GENERATE_REPORTS_FORM_ID+"-button').attr('disabled',false).text('Generate Report');"
                + "    var charts = JSON.parse(data.charts); "
                + "    for(var i = 0; i<charts.length; i++) { "
                + "       Highcharts.chart('chart-'+i.toString(), charts[i]);"
                + "    }  "
                + "  }"
                + "});"
                + "return false; "
                )).with(h2("Company Profiler"),
                h3("Company Information"),
                label("Company Name"),br(),input().withType("text").withName("assignee"),br(),
                SimilarPatentServer.expandableDiv("Report Types",false,div().with(
                        h4("Report Types"),
                        div().with(reportTypes.stream().sorted().map(type->{
                                    EmptyTag radio = isFirst.getAndSet(false)?input().attr("checked","checked"):input();
                                    return div().with(
                                            label().with(radio.withType("radio").withName("report_type").withValue(type),span(type).attr("style","margin-left:7px;")),br()
                                    );
                                }).collect(Collectors.toList())
                        ),br()
                )),
                br(),
                button("Generate Report").withId(GENERATE_REPORTS_FORM_ID+"-button").withType("submit"))
        );
    }

    private static Tag navigationTag() {
        return div().with(SimilarPatentServer.formScript(GENERATE_REPORTS_FORM_ID+"-back", "/company_profile_report", "Back",true),
                form().withId(GENERATE_REPORTS_FORM_ID+"-back").with(
                        input().withName("goBack").withValue("on").withType("hidden"), br(),
                        button("Back").withId(GENERATE_REPORTS_FORM_ID+"-back"+"-button").withType("submit")
                ),SimilarPatentServer.formScript(GENERATE_REPORTS_FORM_ID+"-forward", "/company_profile_report", "Forward",true),
                form().withId(GENERATE_REPORTS_FORM_ID+"-forward").with(
                        input().withName("goForward").withValue("on").withType("hidden"), br(),
                        button("Forward").withId(GENERATE_REPORTS_FORM_ID+"-forward"+"-button").withType("submit")
                ));
    }

    static void setupServer() {
        Spark.staticFileLocation("/public");
        // Host my own image asset!
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

        get("/company_profile", (req, res) -> SimilarPatentServer.templateWrapper(res, div().with(generateReportsForm(), hr()), SimilarPatentServer.getAndRemoveMessage(req.session())));


        post("/company_profile_report", (req, res) -> {
            // handle navigation

            BackButtonHandler navigator;
            if(req.session().attribute("navigator")==null) {
                navigator = new BackButtonHandler(req);
                req.session().attribute("navigator",navigator);
            } else {
                navigator = req.session().attribute("navigator");
            }

            if(SimilarPatentServer.extractBool(req, "goBack")) {
                Request tmp = navigator.goForward();
                if(tmp==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go back"));
                req=tmp;
            } else if(SimilarPatentServer.extractBool(req, "goForward")) {
                Request tmp = navigator.goForward();
                if(tmp==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go forward"));
                req=tmp;
            } else {
                navigator.addRequest(req);
            }

            res.type("application/json");
            String assigneeStr = SimilarPatentServer.extractString(req,"assignee",null);
            String patentStr = SimilarPatentServer.extractString(req,"patent",null);
            String reportType = req.queryParams("report_type");
            if(assigneeStr==null||assigneeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter a Company"));
            if(reportType==null||reportType.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter a Report Type"));

            PortfolioList.Type inputType = assigneeStr==null? PortfolioList.Type.patents: PortfolioList.Type.assignees;
            List<AbstractChart> charts = new ArrayList<>();
            int limit = SimilarPatentServer.extractInt(req,"limit",20);
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
            Comparator<ExcelWritable> comparator;
            boolean portfolioValuation=false;
            boolean recentTimeline = false;
            boolean useAttributes = true;
            boolean comparingByValue=false;
            PortfolioList portfolioList;

            // pre data
            Collection<String> patentsToSearchIn;
            List<String> customAssigneeList;
            Set<String> labelsToExclude;
            switch(reportType) {
                case "Recent Activity Timeline": {
                    recentTimeline=true;
                    searchEntireDatabase=false;
                    useSimilarPatentFinders=false;
                    patentsToSearchIn = null;
                    customAssigneeList = null;
                    assigneesToSearchFor=null;
                    patentsToSearchFor=null;
                    classCodesToSearchFor=null;
                    useAttributes=false;
                    portfolioType=null;
                    labelsToExclude=null;
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=false;
                    searchType=null;
                    comparator=null;
                    break;
                }
                case "Company Valuation": {
                    portfolioValuation=true;
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
                    comparator=ExcelWritable.valueComparator();
                    comparingByValue=true;
                    break;
                }
                case "Representative Patents": {
                    searchEntireDatabase=false;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = Database.selectPatentNumbersFromAssignee(assigneeStr);
                    customAssigneeList = Collections.emptyList();
                    assigneesToSearchFor=new HashSet<>(Arrays.asList(assigneeStr));
                    patentsToSearchFor=Collections.emptySet();
                    classCodesToSearchFor=Collections.emptySet();
                    portfolioType= PortfolioList.Type.patents;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=true;
                    searchType="patents";
                    comparator=ExcelWritable.similarityComparator();
                    break;
                }
                case "Similar Patent Finder": {
                    searchEntireDatabase=true;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = null;
                    customAssigneeList = null;
                    assigneesToSearchFor=new HashSet<>(Arrays.asList(assigneeStr));
                    patentsToSearchFor=Collections.emptySet();
                    classCodesToSearchFor=Collections.emptySet();
                    portfolioType= PortfolioList.Type.patents;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=false;
                    searchType="patents";
                    comparator=ExcelWritable.similarityComparator();
                    break;
                }
                case "Similar Company Finder": {
                    searchEntireDatabase=true;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = null;
                    customAssigneeList = null;
                    assigneesToSearchFor=new HashSet<>(Arrays.asList(assigneeStr));
                    patentsToSearchFor=new HashSet<>();
                    classCodesToSearchFor=new HashSet<>();
                    portfolioType= PortfolioList.Type.assignees;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=false;
                    searchType="assignees";
                    comparator=ExcelWritable.similarityComparator();
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
            List<String> attributes = new ArrayList<>(10);
            if(useAttributes) {
                if(!attributesMap.containsKey(reportType)) return new Gson().toJson(new SimpleAjaxMessage("Attributes not defined for Report Type: "+reportType));
                attributes.addAll(attributesMap.get(reportType));
            }
            if(useSimilarPatentFinders) {
                System.out.println("Using similar patent finders");
                firstFinder = SimilarPatentServer.getFirstPatentFinder(labelsToExclude, customAssigneeList, patentsToSearchIn, new HashSet<>(), searchEntireDatabase, includeSubclasses, allowResultsFromOtherCandidateSet, searchType, patentsToSearchFor, assigneesToSearchFor, classCodesToSearchFor);

                if (firstFinder == null || firstFinder.getPatentList().size() == 0) {
                    return new Gson().toJson(new SimpleAjaxMessage("Unable to find any results to search in."));
                }

                List<SimilarPatentFinder> secondFinders = SimilarPatentServer.getSecondPatentFinder(mergeSearchInput, patentsToSearchFor, assigneesToSearchFor, classCodesToSearchFor);

                if (secondFinders.isEmpty() || secondFinders.stream().collect(Collectors.summingInt(finder -> finder.getPatentList().size())) == 0) {
                    return new Gson().toJson(new SimpleAjaxMessage("Unable to find any of the search inputs."));
                }

                System.out.println("Starting to run similar patent model...");
                portfolioList = SimilarPatentServer.runPatentFinderModel(reportType, firstFinder, secondFinders, limit, 0.0, labelsToExclude, new HashSet<>(), portfolioType);
                System.out.println("Finished similar patent model.");


            } else if (portfolioValuation) {
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
                ColumnChart columnChart = new ColumnChart("Valuation for "+assigneeStr, HighchartDataAdapter.collectAverageCompanyValueData(assigneeStr,SimilarPatentServer.modelMap.entrySet().stream().map(e->e.getValue()).collect(Collectors.toList())),1.0,5.0);
                charts.add(columnChart);

                System.out.println("Starting building portfolio list");
                portfolioList = PortfolioList.abstractPorfolioList(toSearchIn, portfolioType);
                System.out.println("Finished building portfolio list");
            } else if(recentTimeline) {
                portfolioList=null;
                LineChart lineChart = new LineChart("Recent Activity Timeline for "+assigneeStr, HighchartDataAdapter.collectCompanyActivityData(assigneeStr), AxisType.DATETIME);
                charts.add(lineChart);

            } else {
                return new Gson().toJson(new SimpleAjaxMessage("Unrecognized options."));
            }


            if(portfolioList!=null) {
                System.out.println("Starting values");

                // Handle overall value
                if(comparingByValue) {
                    SimilarPatentServer.modelMap.forEach((key, model) -> {
                        if ((attributes.contains("overallValue") || attributes.contains(key)) && model != null) {
                            SimilarPatentServer.evaluateModel(model, portfolioList.getPortfolio(), key);
                        }
                    });
                    System.out.println("Starting overall value");
                    if (attributes.contains("overallValue")) {
                        portfolioList.computeAvgValues();
                    }
                    System.out.println("Finished overall value");
                    portfolioList.init(comparator, limit);
                } else {
                    // faster to init results first
                    portfolioList.init(comparator, limit);
                    if (attributes.contains("overallValue")) {
                        portfolioList.computeAvgValues();
                    }
                    SimilarPatentServer.modelMap.forEach((key, model) -> {
                        if ((attributes.contains("overallValue") || attributes.contains(key)) && model != null) {
                            SimilarPatentServer.evaluateModel(model, portfolioList.getPortfolio(), key);
                        }
                    });
                }

                if(useSimilarPatentFinders) {
                    BarChart barChart = new BarChart("Similarity to " + assigneeStr, HighchartDataAdapter.collectSimilarityData(assigneeStr, portfolioList), 0d, 100d, "%");
                    charts.add(barChart);
                }
            }

            System.out.println("Finished initializing portfolio");

            AtomicInteger chartCnt = new AtomicInteger(0);

            try {
            return new Gson().toJson(new AjaxChartMessage(div().with(
                    navigationTag(),hr(),
                    h3(reportType+" for "+assigneeStr),
                    charts.isEmpty()?div():div().with(
                            h4("Charts"),
                            div().with(
                                    charts.stream().map(c->div().withId("chart-"+chartCnt.getAndIncrement())).collect(Collectors.toList())
                            )
                    ),
                    portfolioList==null?div():div().with(
                            h4("Data"),
                            tableFromPatentList(portfolioList.getPortfolio(), attributes)
                    )
            ).render(),charts));

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
