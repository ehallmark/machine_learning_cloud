package server;

import com.google.gson.Gson;
import genetics.lead_development.Attribute;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import seeding.Database;
import server.tools.BackButtonHandler;
import server.tools.SimpleAjaxMessage;
import spark.QueryParamsMap;
import tools.AssigneeTrimmer;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.classification.CPCGatherTechTagger;
import ui_models.attributes.classification.KeywordGatherTechTagger;
import ui_models.attributes.classification.SimilarityGatherTechTagger;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.attributes.value.ValueAttr;
import ui_models.portfolios.PortfolioList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Created by Evan on 2/12/2017.
 */
public class ValuationServer {
    private static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static final String MAIN_INPUT_ID = "main-input-id";
    public static final Map<String,ValueAttr> ATTRIBUTES = new HashMap<>();
    static {
        SimilarPatentServer.loadValueModels();
        SimilarPatentServer.modelMap.forEach((name,model)->ATTRIBUTES.put(name,model));
    }

    static String ajaxSubmitWithChartsScript(String ID,String buttonText, String buttonTextWhileSearching) {
        return "$('#"+ID+"-button').attr('disabled',true).text('"+buttonTextWhileSearching+"...');"
                + "var url = '/valuation_report'; "
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
                    h2("Asset Valuation"),
                    label("To Search For (separated by semi-colon or newline)").with(
                            br(),
                            textarea().withName("search_input")
                    ),br(),
                    div().with(
                            p("(Note: A positive Relative Importance looks to maximize the attribute value, while a negative Relative Importance looks to minimize the attribute value."),
                            table().with(
                                    thead().with(
                                            tr().with(
                                                    th("Attributes").attr("style","text-align: left;"),
                                                    th("Relative Importance").attr("style","text-align: left;")
                                            )
                                    ),tbody().with(
                                            ATTRIBUTES.entrySet().stream().sorted((e1,e2)->e1.getKey().compareTo(e2.getKey())).map(e->label().with(tr().with(
                                                    td(e.getKey()),td().with(input().withType("number").withName("importance-"+e.getKey()).withValue("0"))
                                                    ))
                                            ).collect(Collectors.toList())

                                    )
                            )
                    ),
                    br(),br(),
                    button("Start Search").withId(GENERATE_REPORTS_FORM_ID+"-button").withType("submit"),hr()

                ),
                br(),
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

        get("/asset_valuation", (req, res) -> SimilarPatentServer.templateWrapper(res, generateReportsForm(), SimilarPatentServer.getAndRemoveMessage(req.session())));


        post("/valuation_report", (req, res) -> {
            res.type("application/json");
            long time0 = System.currentTimeMillis();
            try {
                System.out.println("Received request...");
                QueryParamsMap params;

                // handle navigation
                BackButtonHandler<ValueSolution> navigator;
                if (req.session().attribute("navigator") == null) {
                    navigator = new BackButtonHandler<>();
                    req.session().attribute("navigator", navigator);
                } else {
                    navigator = req.session().attribute("navigator");
                }

                if (SimilarPatentServer.extractBool(req, "goBack")) {
                    ValueSolution tmp = navigator.goBack();
                    if (tmp == null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go back"));
                    else {
                        // RETURN SOLUTION
                        System.out.println("Going back");
                        return renderSolution(tmp);
                    }
                } else if (SimilarPatentServer.extractBool(req, "goForward")) {
                    ValueSolution tmp = navigator.goForward();
                    if (tmp == null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go forward"));
                    else {
                        // RETURN SOLUTION
                        System.out.println("Going forward");
                        return renderSolution(tmp);
                    }
                } else {
                    params = req.queryMap();
                }

                System.out.println("Handled navigator");

                List<Pair<ValueAttr,Double>> attrsToUseList = new ArrayList<>(ATTRIBUTES.size());
                ATTRIBUTES.forEach((name,model)->{
                    double importance = SimilarPatentServer.extractDouble(params,"importance-"+name,0d);
                    if(importance!=0) {
                        System.out.println("Using attr " + name + " with weight: " + importance);
                        attrsToUseList.add(new Pair<>(model,importance));
                    }
                });

                String search_input_str = params.get("search_input").value();
                if(search_input_str==null||search_input_str.isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please provide search input."));
                // split by line
                String[] search_inputs = search_input_str.split("\n");

                ValueSolution solution = solve(attrsToUseList,Arrays.asList(search_inputs));
                System.out.println("Solution size: "+solution.scores.size());
                solution.scores.forEach((p)-> System.out.println(p.getFirst()+": "+p.getSecond()));

                // add to request map
                navigator.addRequest(solution);

                return renderSolution(solution);


            } catch(Exception e) {
                return new Gson().toJson(new SimpleAjaxMessage(e.getMessage()));
            } finally {
                long time1 = System.currentTimeMillis();
                System.out.println("Time to complete: "+(time1-time0)/(1000)+" seconds.");
            }
        });
    }

    private static ValueSolution solve(List<Pair<ValueAttr,Double>> modelsWithImportance, Collection<String> searchInputs) {
        return new ValueSolution(searchInputs.stream().map(input->{
            return new Pair<>(input,modelsWithImportance.stream().collect(Collectors.summingDouble(pair->pair.getSecond()*pair.getFirst().evaluate(input))));
        }).collect(Collectors.toList()));
    }

    private static String renderSolution(ValueSolution solution) {
        return new Gson().toJson(new SimpleAjaxMessage(div().with(
                solution == null ? div().with(h4("No Solution Found.")) : div().with(
                        h4("Solution"),
                        tableFromSolution(solution)
                )
        ).render()));
    }

    static Tag tableFromSolution(ValueSolution solution) {
        return div().with(
                table().with(
                        thead().with(
                                tr().with(
                                        th("Asset"),
                                        th("Value")
                                )
                        ),tbody().with(
                                solution.scores.stream().map(entry->tr().with(
                                        td(entry.getFirst()),
                                        td(entry.getSecond().toString())
                                )).collect(Collectors.toList())
                        )
                )

        );
    }

    public static void main(String[] args) throws Exception {
        setupServer();
        System.out.println("Finished Setting up Value Server.");
    }


}
class ValueSolution {
    List<Pair<String,Double>> scores;
    public ValueSolution(List<Pair<String,Double>> scores) {
        this.scores=scores;
    }
}