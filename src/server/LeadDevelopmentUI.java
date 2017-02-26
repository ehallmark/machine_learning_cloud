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
    private static final Map<String,Attribute> attributesMap = new HashMap<>();
    private static final List<String> technologies;
    private static final TechTagger techTagger;

    static {
        techTagger=new NormalizedGatherTagger();
        technologies=new ArrayList<>();
        techTagger.getAllTechnologies().stream().sorted().forEach(tech->technologies.add(tech));
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
        AtomicBoolean isFirst = new AtomicBoolean(true);
        return div().with(form().withId(GENERATE_REPORTS_FORM_ID).attr("onsubmit",
                ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID,"Generate Report","Generating")).with(
                        h2("Company Profiler"),
                        h3("Company Information"),
                        label("Company Name"),br(),input().withId(MAIN_INPUT_ID).withType("text").withName("assignee"),br(),br(),
                        SimilarPatentServer.expandableDiv("Attributes",false,div().with(
                                table().with(
                                        thead().with(
                                                tr().with(
                                                        th("Attributes"),
                                                        th("Relative Importance")
                                                )
                                        ),tbody().with(
                                                attributesMap.entrySet().stream().map(e->label().with(tr().with(
                                                                td(e.getKey()),td().with(input().withType("number").withName("importance-"+e.getValue().getId()).withValue("0"))
                                                        ))
                                                ).collect(Collectors.toList())

                                        )
                                ),br()
                        )),
                        SimilarPatentServer.expandableDiv("Technology",false,div().with(
                                input().withType("number").withName("importance-tech"),
                                select().withName("technologies").attr("multiple","multiple").with(
                                        technologies.stream().map(assignee->option(assignee).withValue(assignee)).collect(Collectors.toList())
                                ),br()
                        )),
                br(),
                button("Search").withId(GENERATE_REPORTS_FORM_ID+"-button").withType("submit")),hr(),
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
            attributesMap.put(ExcelWritable.humanAttributeFor(name),new ValueAttribute(name,0d,model));
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

                int limit = 100;

                List<Attribute> attrsToUseList = new ArrayList<>(attributesMap.size());
                attributesMap.forEach((name,attr)->{
                    Attribute newAttr = attr.dup();
                    newAttr.importance=SimilarPatentServer.extractDouble(params,"importance-"+attr.getId(),0d);
                    if(newAttr.importance>0) {
                        attrsToUseList.add(newAttr);
                    }
                });
                {
                    double technologyImportance = SimilarPatentServer.extractDouble(params,"importance-tech",0d);
                    if(technologyImportance>0) {
                        String[] technologies = params.get("technologies[]").values();
                        if(technologies!=null&&technologies.length>0) {
                            for(String tech : technologies) {
                                Evaluator techModel = new SpecificTechnologyEvaluator(tech,techTagger);
                                attrsToUseList.add(new ValueAttribute(tech,technologyImportance,techModel));
                            }
                        }
                    }
                }

                CompanySolution solution = runGeneticAlgorithm(attrsToUseList, limit);
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

    static CompanySolution runGeneticAlgorithm(List<Attribute> attributes, int limit) {
        int numThreads = Math.max(1,Runtime.getRuntime().availableProcessors()/2);
        CompanySolutionCreator creator = new CompanySolutionCreator(attributes,limit,numThreads);
        GeneticAlgorithm algorithm = new GeneticAlgorithm(creator,limit,new CompanySolutionListener(),numThreads);
        algorithm.simulate(1000,0.5,0.3);
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
