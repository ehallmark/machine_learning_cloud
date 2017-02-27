package server;

import analysis.SimilarPatentFinder;
import analysis.genetics.GeneticAlgorithm;
import analysis.genetics.lead_development.*;
import analysis.tech_tagger.GatherTagger;
import analysis.tech_tagger.NormalizedGatherTagger;
import analysis.tech_tagger.TechTagger;
import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.options.AxisType;
import j2html.tags.EmptyTag;
import j2html.tags.Tag;
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
import value_estimation.SpecificTechnologyEvaluator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Created by Evan on 2/12/2017.
 */
public class LeadDevelopmentUI {
    private static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static final String MAIN_INPUT_ID = "main-input-id";
    private static final Map<String,Attribute> ATTRIBUTES = new HashMap<>();
    private static final List<String> TECHNOLOGIES;
    private static final TechTagger TECH_TAGGER;

    static {
        TECH_TAGGER=new NormalizedGatherTagger();
        TECHNOLOGIES=new ArrayList<>();
        TECH_TAGGER.getAllTechnologies().stream().sorted().forEach(tech->TECHNOLOGIES.add(tech));
    }

    static String ajaxSubmitWithChartsScript(String ID,String buttonText, String buttonTextWhileSearching) {
        return "$('#"+ID+"-button').attr('disabled',true).text('"+buttonTextWhileSearching+"...');"
                + "var url = '/lead_development_report'; "
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
                + "    $('#results').html('<div style=\"color: red;\">Server ajax error</div>'); "
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
                + "         $('#results').html(\"<div style='color:red;'>JavaScript error occured: \" + errorMsg + '</div>');"
                + "      }            "
                + "    }          "
                + "  }        "
                + "});"
                + "return false; ";
    }

    static Tag generateReportsForm() {
        return div().with(form().withId(GENERATE_REPORTS_FORM_ID).attr("onsubmit",
                ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID,"Start Search","Searching")).with(
                        h2("Lead Generation"),
                        h3("Genetic Search"),
                        label("Result Limit").with(
                                br(),
                                input().withType("number").withValue("30").withName("result_limit")
                        ),br(),
                        label("Time Limit (Seconds)").with(
                                br(),
                                input().withType("number").withValue("5").withName("time_limit")
                        ),br(),
                        SimilarPatentServer.expandableDiv("Attributes",false,div().with(
                                table().with(
                                        thead().with(
                                                tr().with(
                                                        th("Attributes").attr("style","text-align: left;"),
                                                        th("Relative Importance").attr("style","text-align: left;")
                                                )
                                        ),tbody().with(
                                                ATTRIBUTES.entrySet().stream().sorted((e1,e2)->e1.getKey().compareTo(e2.getKey())).map(e->label().with(tr().with(
                                                                td(e.getKey()),td().with(input().withType("number").withName("importance-"+e.getValue().getId()).withValue("0"))
                                                        ))
                                                ).collect(Collectors.toList())

                                        )
                                ),br()
                        )),
                        SimilarPatentServer.expandableDiv("Technology Search",false,div().with(
                                h4("Technology Search"),
                                label("Relative Importance").with(br(),
                                        input().withType("number").withValue("0").withName("importance-tech"),br()),
                                label("Technology").with(br(),
                                    select().withName("technology").with(
                                            TECHNOLOGIES.stream().map(assignee->option(assignee).withValue(assignee)).collect(Collectors.toList())
                                    ),br()
                                )
                        )),
                br(),
                button("Start Search").withId(GENERATE_REPORTS_FORM_ID+"-button").withType("submit")),hr(),
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

    static void loadData() {
        SimilarPatentServer.modelMap.forEach((name,model)->{
            ATTRIBUTES.put(ExcelWritable.humanAttributeFor(name),new ValueAttribute(name,0d,model));
        });
    }

    static void setupServer() {
        // load data
        loadData();

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

        get("/lead_development", (req, res) -> SimilarPatentServer.templateWrapper(res, generateReportsForm(), SimilarPatentServer.getAndRemoveMessage(req.session())));


        post("/lead_development_report", (req, res) -> {
            res.type("application/json");
            try {

                System.out.println("Received request...");
                QueryParamsMap params;

                // handle navigation
                BackButtonHandler navigator;
                if (req.session().attribute("navigator") == null) {
                    navigator = new BackButtonHandler();
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

                System.out.println("Handled navigator");
                long timeLimit = ((long)(SimilarPatentServer.extractDouble(params,"time_limit",5d)))*1000;
                int resultLimit = (int)(SimilarPatentServer.extractDouble(params,"result_limit",30d));

                if(resultLimit<1) {
                    return new Gson().toJson(new SimpleAjaxMessage("Please enter a positive result limit"));
                }

                if(timeLimit<1) {
                    return new Gson().toJson(new SimpleAjaxMessage("Please enter a positive time limit"));
                }

                if(timeLimit/1000>60) {
                    return new Gson().toJson(new SimpleAjaxMessage("Time limit should be less than a minute"));
                }

                if(resultLimit>1000) {
                    return new Gson().toJson(new SimpleAjaxMessage("Result limit should be less than a 1000"));
                }

                List<Attribute> attrsToUseList = new ArrayList<>(ATTRIBUTES.size());
                ATTRIBUTES.forEach((name,attr)->{
                    Attribute newAttr = attr.dup();
                    newAttr.importance=SimilarPatentServer.extractDouble(params,"importance-"+attr.getId(),0d);
                    if(newAttr.importance>0) {
                        System.out.println("Using attr: "+name);
                        attrsToUseList.add(newAttr);
                    }
                });
                {
                    double technologyImportance = SimilarPatentServer.extractDouble(params,"importance-tech",0d);
                    System.out.println("Technology Importance: "+technologyImportance);
                    if(technologyImportance>0) {
                        String technology = params.get("technology").value();
                        if(technology!=null&&technology.length()>0) {
                            System.out.println("Using technology: "+technology);
                            Evaluator techModel = new SpecificTechnologyEvaluator(technology,TECH_TAGGER);
                            attrsToUseList.add(new ValueAttribute(technology,technologyImportance,techModel));
                        }
                    }
                }

                // make sure some attributes exist
                if(attrsToUseList.isEmpty()) {
                    return new Gson().toJson(new SimpleAjaxMessage("No attributes found."));
                }

                System.out.println("Starting genetic solution");
                CompanySolution solution = runGeneticAlgorithm(attrsToUseList, resultLimit, timeLimit);
                System.out.println("Finished");

                if(solution==null) return new Gson().toJson(new SimpleAjaxMessage("No solution found"));
                return new Gson().toJson(new SimpleAjaxMessage(div().with(
                        solution == null ? div().with(h4("No Solution Found.")) : div().with(
                                h4("Solution"),
                                tableFromSolution(solution)
                        )
                ).render()));


            } catch(Exception e) {
                return new Gson().toJson(new SimpleAjaxMessage(e.getMessage()));
            }
        });
    }

    static CompanySolution runGeneticAlgorithm(List<Attribute> attributes, int limit, long timeLimit) {
        int numThreads = 1;//Math.max(1,Runtime.getRuntime().availableProcessors()/2);
        CompanySolutionCreator creator = new CompanySolutionCreator(attributes,limit,numThreads);
        GeneticAlgorithm algorithm = new GeneticAlgorithm(creator,30,new CompanySolutionListener(),numThreads);
        System.out.println("Finished initializing genetic algorithm");
        algorithm.simulate(timeLimit,0.5,0.3);
        System.out.println("Finished simulating epochs");
        if(algorithm.getBestSolution()==null)return null;
        return (CompanySolution) (algorithm.getBestSolution());
    }

    static Tag tableFromSolution(CompanySolution solution) {
        return div().with(
                table().with(
                    thead().with(
                            tr().with(
                                    th("Company"),
                                    th("Score")
                            )
                    ),tbody().with(
                            solution.getCompanyScores().stream().map(entry->tr().with(
                                    td(entry.getKey()),
                                    td(entry.getValue().toString())
                            )).collect(Collectors.toList())
                    )
                )

        );
    }

    public static void main(String[] args) throws Exception {
        SimilarPatentServer.loadValueModels();
        setupServer();
        System.out.println("Finished Setting up LD Server.");
    }
}
