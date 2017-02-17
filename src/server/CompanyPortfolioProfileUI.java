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
import spark.QueryParamsMap;
import tools.AssigneeTrimmer;
import tools.PortfolioList;
import value_estimation.Evaluator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
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
    private static final String MAIN_INPUT_ID = "main-input-id";
    private static final List<String> reportTypes;
    private static final Map<String,List<String>> attributesMap;
    static {
        List<String> tmp = new ArrayList<>();
        attributesMap=new HashMap<>();
        List<String> valueAttrs = Arrays.asList("name","assignee","title","citationValue","technologyValue","assigneeValue","marketValue","claimValue","overallValue");
        attributesMap.put("Company/Patent Valuation",valueAttrs);
        List<String> similarPatentAttrs = Arrays.asList("name","similarity","assignee","title");
        attributesMap.put("Representative Patents",similarPatentAttrs);
        attributesMap.put("Valuable Patents",valueAttrs);
        attributesMap.put("Similar Patent Finder",similarPatentAttrs);
        List<String> companyAttrs = Arrays.asList("assignee","totalAssetCount","similarity","relevantAssets");
        attributesMap.put("Similar Company Finder",companyAttrs);
        tmp.addAll(attributesMap.keySet());
        reportTypes=Collections.unmodifiableList(tmp).stream().sorted().collect(Collectors.toList());
    }

    static String ajaxSubmitWithChartsScript(String ID,String buttonText, String buttonTextWhileSearching) {
        return "$('#"+ID+"-button').attr('disabled',true).text('"+buttonTextWhileSearching+"...');"
                + "var url = '/company_profile_report'; "
                + "var tempScrollTop = $(window).scrollTop();"
                + "$.ajax({"
                + "  type: 'POST', "
                + "  dataType: 'json',"
                + "  url: url,     "
                + "  data: $('#"+ID+"').serialize(),"
                + "  complete: function(jqxhr,status) {"
                + "    $('#"+ID+"-button').attr('disabled',false).text('"+buttonText+"');"
                + "    $(window).scrollTop(tempScrollTop);"
                + "  },"
                + "  error: function(jqxhr,status,error) {"
                + "    $('#results').html('<div style=\"color: red;\">Server ajax error</div>'); "
                + "  },"
                + "  success: function(data) { "
                + "    $('#results').html(data.message); "
                + "    if (data.hasOwnProperty('charts')) {                    "
                + "      var charts = JSON.parse(data.charts);                 "
                + "      for(var i = 0; i<charts.length; i++) {  "
                + "         var clickable = $('#chart-'+i.toString()).attr('ajaxclickable');     "
                + "         if(typeof attr !== typeof undefined && attr !== false && attr.toLowerCase() !== 'false') {"
                + "             charts[i].plotOptions.series.point.events.dblclick=function() {"
                + "                 $('#"+MAIN_INPUT_ID+"').val(this.name); $('#" + MAIN_INPUT_ID + "').closest('form').submit();"
                + "             };     "
                + "         }       "
                + "         $('#chart-'+i.toString()).highcharts(charts[i]);"
                + "      }                        "
                + "    }                          "
                + "  }                            "
                + "});"
                + "return false; ";
    }

    static Tag generateReportsForm() {
        AtomicBoolean isFirst = new AtomicBoolean(true);
        return div().with(form().withId(GENERATE_REPORTS_FORM_ID).attr("onsubmit",
                ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID,"Generate Report","Generating")).with(
                        h2("Company Profiler"),
                        h3("Company Information"),
                        label("Company Name"),br(),input().withId(MAIN_INPUT_ID).withType("text").withName("assignee"),br(),br(),
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
                button("Generate Report").withId(GENERATE_REPORTS_FORM_ID+"-button").withType("submit")),hr(),
                navigationTag(),br(),br(),br()
        );
    }

    private static Tag navigationTag() {
        return div().with(
                form().attr("onsubmit",ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID+"-back","Back","Going back"))
                        .attr("style","float: left;").withId(GENERATE_REPORTS_FORM_ID+"-back").with(
                            input().withName("goBack").withValue("on").withType("hidden"), br(),
                            button("Back").withId(GENERATE_REPORTS_FORM_ID+"-back"+"-button").withType("submit")
                ),
                form().attr("onsubmit",ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID+"-forward","Forward","Going forward"))
                        .attr("style","float: right;").withId(GENERATE_REPORTS_FORM_ID+"-forward").with(
                        input().withName("goForward").withValue("on").withType("hidden"), br(),
                        button("Forward").withId(GENERATE_REPORTS_FORM_ID+"-forward"+"-button").withType("submit")
                ));
    }

    static void setupServer() {
        get("/js/customEvents.js",(request, response) -> {
            response.type("text/javascript");

            String pathToFile = "public/js/customEvents.js";
            File f = new File(pathToFile);

            OutputStream out = response.raw().getOutputStream();
            BufferedReader reader = new BufferedReader(new FileReader(f));
            reader.lines().forEach(line->{
                try {
                    out.write(line.getBytes());
                    out.write("\n".getBytes());
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });


            out.close();
            response.status(200);
            return response.body();
        });

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

        get("/company_profile", (req, res) -> SimilarPatentServer.templateWrapper(res, generateReportsForm(), SimilarPatentServer.getAndRemoveMessage(req.session())));


        post("/company_profile_report", (req, res) -> {
            res.type("application/json");

            QueryParamsMap params;

            // handle navigation
            BackButtonHandler navigator;
            if(req.session().attribute("navigator")==null) {
                navigator = new BackButtonHandler();
                req.session().attribute("navigator",navigator);
            } else {
                navigator = req.session().attribute("navigator");
            }

            if(SimilarPatentServer.extractBool(req, "goBack")) {
                QueryParamsMap tmp = navigator.goBack();
                if(tmp==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go back"));
                params=tmp;
            } else if(SimilarPatentServer.extractBool(req, "goForward")) {
                QueryParamsMap tmp = navigator.goForward();
                if(tmp==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go forward"));
                params=tmp;
            } else {
                params=req.queryMap();
                navigator.addRequest(new QueryParamsMap(req.raw()));
            }


            final String portfolioString = params.get("assignee").value();
            if(portfolioString==null||portfolioString.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter a Company or a Patent"));
            PortfolioList.Type inputType;
            String assigneeStr = AssigneeTrimmer.standardizedAssignee(portfolioString);
            String patentStr = portfolioString.replaceAll("[^0-9]","");
            final String cleanPortfolioString;
            if (Database.isAssignee(assigneeStr)) {
                inputType = PortfolioList.Type.assignees;
                cleanPortfolioString=assigneeStr;
            } else if (Database.isPatent(patentStr)) {
                inputType = PortfolioList.Type.patents;
                cleanPortfolioString=patentStr;
            } else {
                return new Gson().toJson(new SimpleAjaxMessage("Unable to find " + portfolioString));
            }

            String reportType = params.get("report_type").value();
            if(reportType==null||reportType.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter a Report Type"));

            List<AbstractChart> charts = new ArrayList<>();
            int limit = 20;
            SimilarPatentFinder firstFinder;
            boolean includeSubclasses = false;
            boolean allowResultsFromOtherCandidateSet;
            boolean searchEntireDatabase;
            boolean mergeSearchInput;
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
            PortfolioList portfolioList=null;
            boolean ajaxClickablePoints = false;

            // pre data
            Collection<String> patentsToSearchIn;
            List<String> customAssigneeList;
            Set<String> labelsToExclude;
            switch(reportType) {
                case "Recent Activity Timeline": {
                    if(inputType.equals(PortfolioList.Type.patents)) return new Gson().toJson(new SimpleAjaxMessage("Must search for a company to use this option"));
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
                    comparator=null;
                    break;
                }
                case "Company/Patent Valuation": {
                    portfolioValuation=true;
                    searchEntireDatabase=false;
                    useSimilarPatentFinders=false;
                    patentsToSearchIn = null;
                    customAssigneeList = Collections.emptyList();
                    if(inputType.equals(PortfolioList.Type.assignees)) {
                        assigneesToSearchFor= Database.possibleNamesForAssignee(cleanPortfolioString);
                        patentsToSearchFor=Collections.emptySet();
                    } else {
                        //patents
                        assigneesToSearchFor=Collections.emptySet();
                        patentsToSearchFor=new HashSet<>(Arrays.asList(cleanPortfolioString));
                    }
                    classCodesToSearchFor=Collections.emptySet();
                    portfolioType= PortfolioList.Type.assignees;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=false;
                    comparator=ExcelWritable.valueComparator();
                    comparingByValue=true;
                    break;
                }
                case "Valuable Patents": {
                    if(inputType.equals(PortfolioList.Type.patents)) return new Gson().toJson(new SimpleAjaxMessage("Must search for a company to use this option"));
                    portfolioValuation=false;
                    searchEntireDatabase=false;
                    useSimilarPatentFinders=false;
                    patentsToSearchIn = Collections.emptySet();
                    customAssigneeList = Collections.emptyList();
                    assigneesToSearchFor=Collections.emptySet();
                    patentsToSearchFor=Collections.emptySet();
                    classCodesToSearchFor=Collections.emptySet();
                    portfolioType= PortfolioList.Type.patents;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=false;
                    allowResultsFromOtherCandidateSet=false;
                    comparator=ExcelWritable.valueComparator();
                    comparingByValue=true;
                    portfolioList=PortfolioList.abstractPorfolioList(Database.selectPatentNumbersFromAssignee(cleanPortfolioString),portfolioType);
                    break;
                }
                case "Representative Patents": {
                    if(inputType.equals(PortfolioList.Type.patents)) return new Gson().toJson(new SimpleAjaxMessage("Must search for a company to use this option"));
                    searchEntireDatabase=false;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = Database.selectPatentNumbersFromAssignee(cleanPortfolioString);
                    customAssigneeList = Collections.emptyList();
                    assigneesToSearchFor=Database.possibleNamesForAssignee(cleanPortfolioString);
                    patentsToSearchFor=Collections.emptySet();
                    classCodesToSearchFor=Collections.emptySet();
                    portfolioType= PortfolioList.Type.patents;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=true;
                    allowResultsFromOtherCandidateSet=true;
                    comparator=ExcelWritable.similarityComparator();
                    break;
                }
                case "Similar Patent Finder": {
                    searchEntireDatabase=true;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = null;
                    customAssigneeList = null;
                    if(inputType.equals(PortfolioList.Type.assignees)) {
                        assigneesToSearchFor= Database.possibleNamesForAssignee(cleanPortfolioString);
                        patentsToSearchFor=Collections.emptySet();
                    } else {
                        //patents
                        assigneesToSearchFor=Collections.emptySet();
                        patentsToSearchFor=new HashSet<>(Arrays.asList(cleanPortfolioString));
                    }
                    classCodesToSearchFor=Collections.emptySet();
                    portfolioType= PortfolioList.Type.patents;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=true;
                    allowResultsFromOtherCandidateSet=false;
                    comparator=ExcelWritable.similarityComparator();
                    break;
                }
                case "Similar Company Finder": {
                    searchEntireDatabase=true;
                    useSimilarPatentFinders=true;
                    patentsToSearchIn = null;
                    customAssigneeList = null;
                    if(inputType.equals(PortfolioList.Type.assignees)) {
                        assigneesToSearchFor= Database.possibleNamesForAssignee(cleanPortfolioString);
                        patentsToSearchFor=Collections.emptySet();
                    } else {
                        //patents
                        assigneesToSearchFor=Collections.emptySet();
                        patentsToSearchFor=new HashSet<>(Arrays.asList(cleanPortfolioString));
                    }
                    classCodesToSearchFor=Collections.emptySet();
                    portfolioType= PortfolioList.Type.assignees;
                    labelsToExclude=new HashSet<>();
                    mergeSearchInput=true;
                    allowResultsFromOtherCandidateSet=false;
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
                firstFinder = SimilarPatentServer.getFirstPatentFinder(labelsToExclude, customAssigneeList, patentsToSearchIn, new HashSet<>(), searchEntireDatabase, includeSubclasses, allowResultsFromOtherCandidateSet, inputType.toString(), patentsToSearchFor, assigneesToSearchFor, classCodesToSearchFor);

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
                portfolioList=null;
                System.out.println("Using abstract portfolio type");
                AbstractChart chart = new ColumnChart("Valuation for "+portfolioString, HighchartDataAdapter.collectAverageValueData(cleanPortfolioString,inputType,SimilarPatentServer.modelMap.entrySet().stream().map(e->e.getValue()).collect(Collectors.toList())),1.0,5.0);
                // test!
                charts.add(chart);

            } else if(recentTimeline) {
                portfolioList=null;
                LineChart lineChart = new LineChart("Recent Activity Timeline for "+portfolioString, HighchartDataAdapter.collectCompanyActivityData(cleanPortfolioString), AxisType.DATETIME);
                charts.add(lineChart);

            } else if(portfolioList==null) {
                return new Gson().toJson(new SimpleAjaxMessage("Unrecognized options."));
            }


            if(portfolioList!=null) {
                System.out.println("Starting values");

                // Handle overall value
                if(comparingByValue) {
                    for(Map.Entry<String, Evaluator> e : SimilarPatentServer.modelMap.entrySet()) {
                        String key = e.getKey();
                        Evaluator model = e.getValue();
                        if ((attributes.contains("overallValue") || attributes.contains(key)) && model != null) {
                            SimilarPatentServer.evaluateModel(model, portfolioList.getPortfolio(), key);
                        }
                    }
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
                    for(Map.Entry<String, Evaluator> e : SimilarPatentServer.modelMap.entrySet()) {
                        String key = e.getKey();
                        Evaluator model = e.getValue();
                        if ((attributes.contains("overallValue") || attributes.contains(key)) && model != null) {
                            SimilarPatentServer.evaluateModel(model, portfolioList.getPortfolio(), key);
                        }
                    }
                }

                if(useSimilarPatentFinders) {
                    BarChart barChart = new BarChart("Similarity to " + portfolioString, HighchartDataAdapter.collectSimilarityData(cleanPortfolioString, portfolioList), 0d, 100d, "%");
                    ajaxClickablePoints=true;
                    charts.add(barChart);
                } else if (reportType.equals("Valuable Patents")) {
                    BarChart barChart = new BarChart("Valuable Patents for " + portfolioString, HighchartDataAdapter.collectValueData(cleanPortfolioString, portfolioList), 1d, 5d);
                    ajaxClickablePoints=true;
                    charts.add(barChart);
                }
            }

            // TESTING
            ajaxClickablePoints=true;

            System.out.println("Finished initializing portfolio");

            AtomicInteger chartCnt = new AtomicInteger(0);

            final boolean useAjaxClickablePoints = ajaxClickablePoints;
            try {
            return new Gson().toJson(new AjaxChartMessage(div().with(
                    h3(reportType+" for "+portfolioString),
                    charts.isEmpty()?div():div().with(
                            h4("Charts"),
                            div().with(
                                    charts.stream().map(c->(useAjaxClickablePoints?div().attr("ajaxclickable","true"):div()).withId("chart-"+chartCnt.getAndIncrement())).collect(Collectors.toList())
                            )
                    ),
                    portfolioList==null?div():div().with(
                            h4("Data"),
                            tableFromPatentList(portfolioList.getPortfolio(), attributes)
                    )
            ).render(),charts));

            } catch(Exception e) {
                e.printStackTrace();
                return new Gson().toJson(new SimpleAjaxMessage("Failed to render data"));
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
                        items.stream().map(item->tr().with(
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
