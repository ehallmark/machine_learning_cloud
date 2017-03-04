package server;

import analysis.WordFrequencyPair;
import analysis.genetics.GeneticAlgorithm;
import analysis.genetics.Solution;
import analysis.genetics.lead_development.*;
import analysis.tech_tagger.GatherTagger;
import analysis.tech_tagger.SimilarityTechTagger;
import analysis.tech_tagger.TechTagger;
import analysis.tech_tagger.TechTaggerNormalizer;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import server.tools.BackButtonHandler;
import server.tools.SimpleAjaxMessage;
import server.tools.excel.ExcelWritable;
import spark.QueryParamsMap;
import tools.AssigneeTrimmer;
import tools.MinHeap;
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
public class TechTaggerUI {
    private static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static final String MAIN_INPUT_ID = "main-input-id";
    private static final TechTagger CPC_TAGGER;
    private static final TechTagger SIMILARITY_TAGGER;
    private static final TechTagger TAGGER;
    static {
        CPC_TAGGER=new GatherTagger();
        SIMILARITY_TAGGER=new SimilarityTechTagger(Database.getGatherTechMap(),SimilarPatentServer.getLookupTable());
        TAGGER = new TechTaggerNormalizer(Arrays.asList(CPC_TAGGER,SIMILARITY_TAGGER),Arrays.asList(0.5,1.0));
    }

    static String ajaxSubmitWithChartsScript(String ID,String buttonText, String buttonTextWhileSearching) {
        return "$('#"+ID+"-button').attr('disabled',true).text('"+buttonTextWhileSearching+"...');"
                + "var url = '/tech_tagger_report'; "
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
                        h2("Tech Tagger"),
                        label("To Search For").with(
                                br(),
                                input().withType("text").withName("search_input")
                        ),br(),
                        label("Tag Limit").with(
                                br(),
                                input().withType("number").withValue("5").withName("tag_limit")
                        ),br(),br(),
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

            String pathToImage = "images/brand.png";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            response.status(200);
            return response.body();
        });

        get("/tech_tagger", (req, res) -> SimilarPatentServer.templateWrapper(res, generateReportsForm(), SimilarPatentServer.getAndRemoveMessage(req.session())));


        post("/tech_tagger_report", (req, res) -> {
            res.type("application/json");
            try {

                System.out.println("Received request...");
                QueryParamsMap params;

                // handle navigation
                BackButtonHandler<TechnologySolution> navigator;
                if (req.session().attribute("navigator") == null) {
                    navigator = new BackButtonHandler<>();
                    req.session().attribute("navigator", navigator);
                } else {
                    navigator = req.session().attribute("navigator");
                }

                if (SimilarPatentServer.extractBool(req, "goBack")) {
                    TechnologySolution tmp = navigator.goBack();
                    if (tmp == null) return new Gson().toJson(new SimpleAjaxMessage("Unable to go back"));
                    else {
                        // RETURN SOLUTION
                        System.out.println("Going back");
                        return renderSolution(tmp);
                    }
                } else if (SimilarPatentServer.extractBool(req, "goForward")) {
                    TechnologySolution tmp = navigator.goForward();
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
                int tag_limit = (int)(SimilarPatentServer.extractDouble(params,"tag_limit",30d));
                String search_input = params.get("search_input").value();
                if(search_input==null||search_input.isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please provide search input."));

                PortfolioList.Type inputType;
                String assigneeStr = AssigneeTrimmer.standardizedAssignee(search_input);
                String patentStr = search_input.replaceAll("[^0-9]", "");
                final String cleanSearchInput;
                if (Database.isAssignee(assigneeStr)) {
                    inputType = PortfolioList.Type.assignees;
                    cleanSearchInput = assigneeStr;
                } else if (Database.isPatent(patentStr)) {
                    inputType = PortfolioList.Type.patents;
                    cleanSearchInput = patentStr;
                } else {
                    return new Gson().toJson(new SimpleAjaxMessage("Unable to find " + search_input));
                }

                System.out.println("Starting similarity tagger");
                List<Pair<String,Double>> results = TAGGER.getTechnologiesFor(cleanSearchInput, inputType, tag_limit);

                TechnologySolution solution = new TechnologySolution(results);

                if(solution==null) return new Gson().toJson(new SimpleAjaxMessage("No solution found"));

                // add to request map
                navigator.addRequest(solution);

                return renderSolution(solution);


            } catch(Exception e) {
                return new Gson().toJson(new SimpleAjaxMessage(e.getMessage()));
            }
        });
    }

    private static String renderSolution(TechnologySolution solution) {
        return new Gson().toJson(new SimpleAjaxMessage(div().with(
                solution == null ? div().with(h4("No Solution Found.")) : div().with(
                        h4("Solution"),
                        tableFromSolution(solution)
                )
        ).render()));
    }

    static Tag tableFromSolution(TechnologySolution solution) {
        return div().with(
                table().with(
                    thead().with(
                            tr().with(
                                    th("Technology"),
                                    th("Score")
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
        SimilarPatentServer.loadValueModels();
        setupServer();
        System.out.println("Finished Setting up LD Server.");
    }


}
class TechnologySolution {
    List<Pair<String,Double>> scores;
    public TechnologySolution(List<Pair<String,Double>> scores) {
        this.scores=scores;
    }
}