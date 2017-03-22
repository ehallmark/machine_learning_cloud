package server;

import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.options.AxisType;
import j2html.tags.*;
import seeding.Constants;
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
import value_estimation.OverallEvaluator;
import value_estimation.ValueMapNormalizer;

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
        attributesMap=new HashMap<>();
        List<String> valueAttrs = Arrays.asList("name","assignee","title","overallValue");
        attributesMap.put("Portfolio Valuation",valueAttrs);
        List<String> similarPatentAttrs = Arrays.asList("name","similarity","assignee","title");
        attributesMap.put("Representative Patents",similarPatentAttrs);
        attributesMap.put("Valuable Patents",valueAttrs);
        attributesMap.put("Similar Patent Finder",similarPatentAttrs);
        List<String> companyAttrs = Arrays.asList("assignee","totalAssetCount","similarity","relevantAssets");
        attributesMap.put("Similar Portfolio Finder",companyAttrs);
        List<String> techAttrs = Arrays.asList("name","technology");
        attributesMap.put("Technology Distribution",techAttrs);
        attributesMap.put("Company Details", Collections.emptyList());
        attributesMap.put("Recent Activity Timeline",Collections.emptyList());
        attributesMap.put("Recent Technology Timeline",Collections.emptyList());
        attributesMap.put("Recent Value Timeline",Collections.emptyList());
        reportTypes=attributesMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    static String ajaxSubmitWithChartsScript(String ID,String buttonText, String buttonTextWhileSearching) {
        return "$('#"+ID+"-button').attr('disabled',true).text('"+buttonTextWhileSearching+"...');"
                + "var url = '/company_profile_report'; "
                + "var tempScrollTop = $(window).scrollTop();"
                //+ "window.onerror = function(errorMsg, url, lineNumber) {"
                //+ "    $('#results').html(\"<div style='color:red;'>JavaScript error occured: \" + errorMsg + '</div>');"
                //+ "    $('#"+ID+"-button').attr('disabled',false).text('"+buttonText+"');"
                //+ "    return false;"
                //+ "};"
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
                + "    $('#results').html('<div style=\"color: red;\">Server ajax error:'+error+'</div>'); "
                + "  },"
                + "  success: function(data) { "
                + "    $('#results').html(data.message); "
                + "    if (data.hasOwnProperty('charts')) {                    "
                + "      try {    "
                + "         var charts = JSON.parse(data.charts);                 "
                + "         for(var i = 0; i<charts.length; i++) {  "
                + "             var clickable = $('#chart-'+i.toString()).attr('ajaxclickable');     "
                + "             if((typeof clickable !== typeof undefined) && (clickable !== false)) {"
                + "                 charts[i].plotOptions.series.point.events.dblclick=function() {"
                + "                     $('#"+MAIN_INPUT_ID+"').val(this.name); $('#" + MAIN_INPUT_ID + "').closest('form').submit();"
                + "                 };     "
                + "             }       "
                + "             $('#chart-'+i.toString()).highcharts(charts[i]);"
                + "         }                        "
                + "      } catch (err) {"
                + "         $('#results').html(\"<div style='color:red;'>JavaScript error occured: \" + err.message + '</div>');"
                + "      }            "
                + "    }          "
                + "  }        "
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

            String pathToImage = Constants.DATA_FOLDER+"images/brand.png";
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
            System.out.println("Received request...");
            try {

                QueryParamsMap params;

                // handle navigation
                BackButtonHandler<QueryParamsMap> navigator;
                if (req.session().attribute("navigator") == null) {
                    navigator = new BackButtonHandler<>();
                    req.session().attribute("navigator", navigator);
                } else {
                    navigator = req.session().attribute("navigator");
                }

                if (SimilarPatentServer.extractBool(req, "goBack")) {
                    QueryParamsMap tmp = navigator.goBack();
                    if (tmp == null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go back"));
                    params = tmp;
                } else if (SimilarPatentServer.extractBool(req, "goForward")) {
                    QueryParamsMap tmp = navigator.goForward();
                    if (tmp == null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go forward"));
                    params = tmp;
                } else {
                    params = req.queryMap();
                    navigator.addRequest(new QueryParamsMap(req.raw()));
                }


                final String portfolioString = params.get("assignee").value();
                if (portfolioString == null || portfolioString.trim().isEmpty())
                    return new Gson().toJson(new SimpleAjaxMessage("Please enter a Company or a Patent"));
                PortfolioList.Type inputType;
                String assigneeStr = AssigneeTrimmer.standardizedAssignee(portfolioString);
                String patentStr = portfolioString.replaceAll("[^0-9]", "");
                final String cleanPortfolioString;
                if (Database.isAssignee(assigneeStr)) {
                    inputType = PortfolioList.Type.assignees;
                    cleanPortfolioString = assigneeStr;
                } else if (Database.isPatent(patentStr)) {
                    inputType = PortfolioList.Type.patents;
                    cleanPortfolioString = patentStr;
                } else {
                    return new Gson().toJson(new SimpleAjaxMessage("Unable to find " + portfolioString));
                }

                String reportType = params.get("report_type").value();
                if (reportType == null || reportType.trim().isEmpty())
                    return new Gson().toJson(new SimpleAjaxMessage("Please enter a Report Type"));

                List<AbstractChart> charts = new ArrayList<>();
                int limit = 20;
                SimilarPatentFinder firstFinder;
                boolean includeSubclasses = false;
                boolean allowResultsFromOtherCandidateSet = false;
                boolean searchEntireDatabase = false;
                boolean mergeSearchInput = true;
                PortfolioList.Type portfolioType = null;
                Set<String> patentsToSearchFor = Collections.emptySet();
                Set<String> classCodesToSearchFor = Collections.emptySet();
                Set<String> assigneesToSearchFor = Collections.emptySet();
                boolean useSimilarPatentFinders = false;
                Comparator<ExcelWritable> comparator = ExcelWritable.similarityComparator();
                boolean comparingByValue = false;
                PortfolioList portfolioList = null;
                boolean ajaxClickablePoints = false;

                // pre data
                Collection<String> patentsToSearchIn = Collections.emptySet();
                List<String> customAssigneeList = Collections.emptyList();
                Set<String> labelsToExclude = new HashSet<>();
                boolean retry = true;
                while (retry) {
                    retry = false;
                    switch (reportType) {
                        case "Company Details": {
                            // COLUMN CHART
                                // assets sold
                                // assets purchased
                                // total assets
                            if (inputType.equals(PortfolioList.Type.patents))
                                return new Gson().toJson(new SimpleAjaxMessage("Must search for a company to use this option"));
                            portfolioList = null;
                            ColumnChart chart = new ColumnChart("Company Details for " + portfolioString, HighchartDataAdapter.collectCompanyDetailsData(cleanPortfolioString), 0d, -1d, " assets", 0);
                            charts.add(chart);
                            break;
                        }
                        case "Recent Activity Timeline": {
                            if (inputType.equals(PortfolioList.Type.patents))
                                return new Gson().toJson(new SimpleAjaxMessage("Must search for a company to use this option"));
                            portfolioList = null;
                            LineChart lineChart = new LineChart("Recent Activity Timeline for " + portfolioString, HighchartDataAdapter.collectCompanyActivityData(cleanPortfolioString), AxisType.DATETIME, 0, " assets", "Asset Count");
                            charts.add(lineChart);
                            break;
                        }
                        case "Recent Technology Timeline": {
                            if (inputType.equals(PortfolioList.Type.patents))
                                return new Gson().toJson(new SimpleAjaxMessage("Must search for a company to use this option"));
                            portfolioList = null;
                            PercentAreaChart chart = new PercentAreaChart("Recent Technology Timeline for " + portfolioString, HighchartDataAdapter.collectTechnologyTimelineData(cleanPortfolioString));
                            charts.add(chart);
                            break;
                        }
                        case "Recent Value Timeline": {
                            if (inputType.equals(PortfolioList.Type.patents))
                                return new Gson().toJson(new SimpleAjaxMessage("Must search for a company to use this option"));
                            portfolioList = null;
                            LineChart chart = new LineChart("Recent Value Timeline for " + portfolioString, HighchartDataAdapter.collectValueTimelineData(cleanPortfolioString, SimilarPatentServer.modelMap.get("overallValue")), AxisType.DATETIME, "", "AI Value");
                            charts.add(chart);
                            break;
                        }
                        case "Valuable Patents": {
                            if (inputType.equals(PortfolioList.Type.patents)) {
                                // switch to Patent Valuation
                                reportType = "Portfolio Valuation";
                                retry = true;
                                break;
                            } else {
                                portfolioType = PortfolioList.Type.patents;
                                comparator = ExcelWritable.valueComparator();
                                comparingByValue = true;
                                portfolioList = PortfolioList.abstractPorfolioList(Database.selectPatentNumbersFromAssignee(cleanPortfolioString), portfolioType);
                                break;
                            }
                        }
                        case "Portfolio Valuation": {
                            if (inputType.equals(PortfolioList.Type.assignees)) {
                                assigneesToSearchFor = Database.possibleNamesForAssignee(cleanPortfolioString);
                            } else {
                                //patents
                                patentsToSearchFor = new HashSet<>(Arrays.asList(cleanPortfolioString));
                            }
                            portfolioType = inputType;
                            comparator = ExcelWritable.valueComparator();
                            comparingByValue = true;
                            portfolioList = null;
                            Set<String> badValueModels = new HashSet<>();
                            badValueModels.add("smallPortfolios");
                            AbstractChart chart = new ColumnChart("Valuation for " + portfolioString, HighchartDataAdapter.collectAverageValueData(cleanPortfolioString, inputType, SimilarPatentServer.modelMap.entrySet().stream().filter(e->!badValueModels.contains(e.getKey())).map(e -> e.getValue()).collect(Collectors.toList())), 1.0, 5.0);
                            // test!
                            charts.add(chart);
                            break;
                        }
                        case "Representative Patents": {
                            if (inputType.equals(PortfolioList.Type.patents)) {
                                // switch to Patent Valuation
                                reportType = "Technology Distribution";
                                retry = true;
                                break;
                            } else {
                                useSimilarPatentFinders = true;
                                patentsToSearchIn = Database.selectPatentNumbersFromAssignee(cleanPortfolioString);
                                assigneesToSearchFor = Database.possibleNamesForAssignee(cleanPortfolioString);
                                portfolioType = PortfolioList.Type.patents;
                                allowResultsFromOtherCandidateSet = true;
                                break;
                            }
                        }
                        case "Similar Patent Finder": {
                            searchEntireDatabase = true;
                            useSimilarPatentFinders = true;
                            if (inputType.equals(PortfolioList.Type.assignees)) {
                                assigneesToSearchFor = Database.possibleNamesForAssignee(cleanPortfolioString);
                            } else {
                                //patents
                                patentsToSearchFor = new HashSet<>(Arrays.asList(cleanPortfolioString));
                            }
                            portfolioType = PortfolioList.Type.patents;
                            break;
                        }
                        case "Similar Portfolio Finder": {
                            searchEntireDatabase = true;
                            useSimilarPatentFinders = true;
                            if (inputType.equals(PortfolioList.Type.assignees)) {
                                assigneesToSearchFor = Database.possibleNamesForAssignee(cleanPortfolioString);
                            } else {
                                //patents
                                patentsToSearchFor = new HashSet<>(Arrays.asList(cleanPortfolioString));
                            }
                            portfolioType = PortfolioList.Type.assignees;
                            break;
                        }
                        case "Technology Distribution": {
                            // special model
                            portfolioType = inputType;
                            portfolioList = null;
                            System.out.println("Using abstract portfolio type");
                            int numTechnologies;
                            if (inputType.equals(PortfolioList.Type.assignees)) numTechnologies = 10;
                            else numTechnologies = 5;
                            AbstractChart chart = new PieChart("Technology Distribution for " + portfolioString, HighchartDataAdapter.collectTechnologyData(cleanPortfolioString, inputType, numTechnologies));
                            // test!
                            charts.add(chart);
                            break;
                        }
                        default: {
                            return new Gson().toJson(new SimpleAjaxMessage("Report option not yet implemented"));
                        }
                    }
                }

                System.out.println("Starting to retrieve portfolio list...");
                if (useSimilarPatentFinders) {
                    System.out.println("Using similar patent finders");
                    firstFinder = SimilarPatentServer.getFirstPatentFinder(labelsToExclude, customAssigneeList, patentsToSearchIn, new HashSet<>(), searchEntireDatabase, includeSubclasses, allowResultsFromOtherCandidateSet, portfolioType.toString(), patentsToSearchFor, assigneesToSearchFor, classCodesToSearchFor);

                    if (firstFinder == null || firstFinder.getPatentList().size() == 0) {
                        return new Gson().toJson(new SimpleAjaxMessage("Unable to find any results to search in."));
                    }

                    List<SimilarPatentFinder> secondFinders = SimilarPatentServer.getSecondPatentFinder(mergeSearchInput, patentsToSearchFor, assigneesToSearchFor, classCodesToSearchFor);

                    if (secondFinders.isEmpty() || secondFinders.stream().collect(Collectors.summingInt(finder -> finder.getPatentList().size())) == 0) {
                        return new Gson().toJson(new SimpleAjaxMessage("Unable to find any of the search inputs."));
                    }

                    System.out.println("Starting to run similar patent model...");
                    double threshold = 0.5;
                    while(threshold>0.0&&(portfolioList==null||portfolioList.getPortfolio().size()<limit)) {
                        portfolioList = SimilarPatentServer.runPatentFinderModel(firstFinder, secondFinders, limit, threshold, labelsToExclude, new HashSet<>(), portfolioType);
                        threshold-=0.2;
                    }
                    System.out.println("Finished similar patent model.");
                }


                if (portfolioList != null) {
                    System.out.println("Handling attrs");
                    List<String> attributes = new ArrayList<>(10);
                    if (!attributesMap.containsKey(reportType))
                        return new Gson().toJson(new SimpleAjaxMessage("Attributes not defined for Report Type: " + reportType));
                    attributes.addAll(attributesMap.get(reportType));
                    portfolioList.setAttributes(attributes);

                    // Handle overall value
                    if (comparingByValue) {
                        for (Map.Entry<String, Evaluator> e : SimilarPatentServer.modelMap.entrySet()) {
                            String key = e.getKey();
                            Evaluator model = e.getValue();
                            if (attributes.contains(key) && model != null) {
                                SimilarPatentServer.evaluateModel(model, portfolioList.getPortfolio(), key);
                            }
                        }
                        portfolioList.init(comparator, limit);
                    } else {
                        // faster to init results first
                        portfolioList.init(comparator, limit);
                        for (Map.Entry<String, Evaluator> e : SimilarPatentServer.modelMap.entrySet()) {
                            String key = e.getKey();
                            Evaluator model = e.getValue();
                            if (attributes.contains(key) && model != null) {
                                SimilarPatentServer.evaluateModel(model, portfolioList.getPortfolio(), key);
                            }
                        }
                    }

                    if (useSimilarPatentFinders) {
                        BarChart barChart = new BarChart("Similarity to " + portfolioString, HighchartDataAdapter.collectSimilarityData(cleanPortfolioString, portfolioList), 0d, 100d, "%");
                        ajaxClickablePoints = true;
                        charts.add(barChart);
                    } else if (reportType.equals("Valuable Patents")) {
                        BarChart barChart = new BarChart("Valuable Patents for " + portfolioString, HighchartDataAdapter.collectValueData(cleanPortfolioString, portfolioList), 1d, 5d);
                        ajaxClickablePoints = true;
                        charts.add(barChart);
                    }
                }

                System.out.println("Finished initializing portfolio");

                AtomicInteger chartCnt = new AtomicInteger(0);

                final boolean useAjaxClickablePoints = ajaxClickablePoints;
                return new Gson().toJson(new AjaxChartMessage(div().with(
                        h3(reportType + " for " + portfolioString),
                        charts.isEmpty() ? div() : div().with(
                                h4("Charts"),
                                div().with(
                                        charts.stream().map(c -> (useAjaxClickablePoints ? div().attr("ajaxclickable", "true") : div()).withId("chart-" + chartCnt.getAndIncrement())).collect(Collectors.toList())
                                )
                        ),
                        portfolioList == null ? div() : div().with(
                                h4("Data"),
                                tableFromPatentList(portfolioList.getPortfolio(), portfolioList.getAttributes())
                        )
                ).render(), charts));


            } catch(Exception e) {
                return new Gson().toJson(new SimpleAjaxMessage(e.getMessage()));
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
        System.out.println("Started server...");
    }
}
