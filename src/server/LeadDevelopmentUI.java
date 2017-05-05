package server;

import analysis.SimilarPatentFinder;
import analysis.WordFrequencyPair;
import analysis.genetics.GeneticAlgorithm;
import analysis.genetics.Solution;
import analysis.genetics.lead_development.*;
import analysis.tech_tagger.*;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.options.AxisType;
import j2html.tags.EmptyTag;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import server.highcharts.*;
import server.tools.AjaxChartMessage;
import server.tools.BackButtonHandler;
import server.tools.SimpleAjaxMessage;
import server.tools.excel.ExcelWritable;
import spark.QueryParamsMap;
import tools.AssigneeTrimmer;
import tools.MinHeap;
import tools.PortfolioList;
import value_estimation.Evaluator;
import value_estimation.SimilarityEvaluator;
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
    public static final Map<String,Attribute> ATTRIBUTES = new HashMap<>();
    private static final List<String> TECHNOLOGIES;
    private static final TechTagger TECH_TAGGER;

    static {
        try {
            SimilarPatentServer.loadLookupTable();
        }catch(Exception e) {
            e.printStackTrace();
        }
        TECH_TAGGER=TechTaggerNormalizer.getDefaultTechTagger();
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
                        ),br(),br(),
                        SimilarPatentServer.expandableDiv("Company Attributes",false,div().with(
                                br(),br(),
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
                                ),br(),br(),
                                table().with(
                                        thead().with(
                                                tr().with(
                                                        th("Portfolio Technology").attr("style","text-align: left;"),
                                                        th("Relative Importance").attr("style","text-align: left;")
                                                )
                                        ),tbody().with(
                                                tr().with(
                                                        td().with(
                                                                select().withName("technology").with(
                                                                        TECHNOLOGIES.stream().map(assignee->option(assignee).withValue(assignee)).collect(Collectors.toList())
                                                                )
                                                        ),
                                                        td().with(
                                                                input().withType("number").withValue("0").withName("importance-tech")
                                                        )
                                                )
                                        )
                                ),br(),br(),
                                table().with(
                                        thead().with(
                                                tr().with(
                                                        th("AI Searches").attr("style","text-align: left;"),
                                                        th("Relative Importance").attr("style","text-align: left;")
                                                )
                                        ),tbody().with(
                                                tr().with(
                                                        td().with(
                                                                input().withType("text").withName("custom-0")
                                                        ),
                                                        td().with(
                                                                input().withType("number").withValue("0").withName("importance-custom-0")
                                                        )
                                                ),
                                                tr().with(
                                                        td().with(
                                                                input().withType("text").withName("custom-1")
                                                        ),
                                                        td().with(
                                                                input().withType("number").withValue("0").withName("importance-custom-1")
                                                        )
                                                ),
                                                tr().with(
                                                        td().with(
                                                                input().withType("text").withName("custom-2")
                                                        ),
                                                        td().with(
                                                                input().withType("number").withValue("0").withName("importance-custom-2")
                                                        )
                                                )
                                        )
                                ),br()
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

            String pathToImage = Constants.DATA_FOLDER+"images/brand.png";
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
                BackButtonHandler<Pair<List<Attribute>,CompanySolution>> navigator;
                if (req.session().attribute("navigator") == null) {
                    navigator = new BackButtonHandler<>();
                    req.session().attribute("navigator", navigator);
                } else {
                    navigator = req.session().attribute("navigator");
                }

                if (SimilarPatentServer.extractBool(req, "goBack")) {
                    Pair<List<Attribute>,CompanySolution> tmp = navigator.goBack();
                    if (tmp == null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go back"));
                    else {
                        // RETURN SOLUTION
                        System.out.println("Going back");
                        return renderSolution(tmp.getSecond(),tmp.getFirst());
                    }
                } else if (SimilarPatentServer.extractBool(req, "goForward")) {
                    Pair<List<Attribute>,CompanySolution> tmp = navigator.goForward();
                    if (tmp == null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go forward"));
                    else {
                        // RETURN SOLUTION
                        System.out.println("Going forward");
                        return renderSolution(tmp.getSecond(),tmp.getFirst());
                    }
                } else {
                    params = req.queryMap();
                }

                System.out.println("Handled navigator");
                long timeLimit = ((long)(SimilarPatentServer.extractDouble(params,"time_limit",5d)))*1000;
                int resultLimit = (int)(SimilarPatentServer.extractDouble(params,"result_limit",30d));
                boolean removeJapanese = (params.value("remove_japanese")!=null&&params.value("remove_japanese").startsWith("on"));

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
                {   // CPC To Tech prediction
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
                {
                    String name = "Custom Search";
                    Collection<INDArray> vectors = new ArrayList<>();
                    boolean shouldUseAIModel = false;
                    boolean noAIInputFound = true;
                    // pull vectors from search input
                    for(int i = 0; i < 10; i++) {
                        double importance = SimilarPatentServer.extractDouble(params,"importance-custom-"+i,0d);
                        if(importance>0) {
                            shouldUseAIModel=true;
                            if (SimilarPatentServer.getLookupTable() == null) {
                                return new Gson().toJson(new SimpleAjaxMessage("No lookup table found for AI Model..."));
                            }
                            String param = params.get("custom-" + i).value();
                            if (param == null || param.length() == 0) continue;

                            String assigneeStr = AssigneeTrimmer.standardizedAssignee(param);
                            String patentStr = param.replaceAll("[^0-9]", "");
                            final String cleanParam;
                            if (Database.isAssignee(assigneeStr)) {
                                cleanParam = assigneeStr;
                            } else if (Database.isPatent(patentStr)) {
                                cleanParam = patentStr;
                            } else {
                                return new Gson().toJson(new SimpleAjaxMessage("Unable to find " + param));
                            }
                            INDArray vec = SimilarPatentServer.getLookupTable().vector(cleanParam);
                            if (vec != null) {
                                SimilarityEvaluator evaluator = new SimilarityEvaluator(name, SimilarPatentServer.getLookupTable(), vec);
                                attrsToUseList.add(new ValueAttribute(name+" - "+param, importance, evaluator));
                                noAIInputFound=false;
                            }
                        }
                    }

                    if(shouldUseAIModel&&noAIInputFound) {
                        // AI Tech prediction
                        if (vectors.isEmpty()) {
                            return new Gson().toJson(new SimpleAjaxMessage("Unable to find search input for AI Model..."));
                        }
                    }
                }

                if(removeJapanese) {

                }

                // make sure some attributes exist
                if(attrsToUseList.isEmpty()) {
                    return new Gson().toJson(new SimpleAjaxMessage("No attributes found."));
                }

                System.out.println("Starting genetic solution");
                CompanySolution solution = runGeneticAlgorithm(attrsToUseList, removeJapanese, resultLimit, timeLimit);
                System.out.println("Finished");

                if(solution==null) return new Gson().toJson(new SimpleAjaxMessage("No solution found"));

                // add to request map
                navigator.addRequest(new Pair<>(attrsToUseList,solution));

                return renderSolution(solution,attrsToUseList);


            } catch(Exception e) {
                return new Gson().toJson(new SimpleAjaxMessage(e.getMessage()));
            }
        });
    }

    private static String renderSolution(CompanySolution solution, List<Attribute> attrs) {
        return new Gson().toJson(new SimpleAjaxMessage(div().with(
                solution == null ? div().with(h4("No Solution Found.")) : div().with(
                        h4("Solution"),
                        tableFromSolution(solution,attrs)
                )
        ).render()));
    }
    static CompanySolution runGeneticAlgorithm(List<Attribute> attributes, boolean removeJapanese, int limit, long timeLimit) {
        int numThreads = Math.max(1,Runtime.getRuntime().availableProcessors()/2);
        CompanySolutionCreator creator = new CompanySolutionCreator(attributes,removeJapanese,limit,numThreads);
        GeneticAlgorithm algorithm = new GeneticAlgorithm(creator,100,new CompanySolutionListener(),numThreads);
        System.out.println("Finished initializing genetic algorithm");
        algorithm.simulate(timeLimit,0.7,0.7);
        System.out.println("Finished simulating epochs");
        Collection<Solution> population = algorithm.getAllSolutions();
        if(population.isEmpty())return null;
        // merge results
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(limit);
        Set<String> alreadySeen = new HashSet<>();
        population.forEach(solution->{
            ((CompanySolution)solution).getCompanyScores().forEach(e->{
                if(!alreadySeen.contains(e.getKey())) {
                    heap.add(new WordFrequencyPair<>(e.getKey(),e.getValue()));
                    alreadySeen.add(e.getKey());
                }
            });
        });

        List<Map.Entry<String,Double>> scores = new ArrayList<>(limit);
        while(!heap.isEmpty()) {
            WordFrequencyPair<String,Double> entry = heap.remove();
            scores.add(0, Maps.immutableEntry(entry.getFirst(),entry.getSecond()));
        }
        return new CompanySolution(scores,attributes);
    }

    static Tag tableFromSolution(CompanySolution solution, List<Attribute> attrs) {
        List<Tag> headers = new ArrayList<>();
        headers.add(th("Company"));
        headers.add(th("Portfolio Size"));
        if(attrs.size()>1)headers.add(th("Overall Score"));
        attrs.forEach(attr->{
            headers.add(th(attr.humanName));
        });
        return div().with(
                table().with(
                    thead().with(
                            tr().with(
                                    headers
                            )
                    ),tbody().with(
                            solution.getCompanyScores().stream().map(entry-> {
                                List<Tag> values = new ArrayList<>();
                                values.add(td(entry.getKey()));
                                values.add(td(String.valueOf(Database.getAssetCountFor(entry.getKey()))));
                                if(attrs.size()>1)values.add(td(entry.getValue().toString()));
                                attrs.forEach(attr->{
                                    values.add(td(String.valueOf(attr.scoreAssignee(entry.getKey()))));
                                });
                                return tr().with(values);
                            }).collect(Collectors.toList())
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
