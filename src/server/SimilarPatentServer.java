package server;

import com.google.gson.Gson;
import highcharts.AbstractChart;
import j2html.tags.ContainerTag;
import j2html.tags.EmptyTag;
import server.tools.AjaxChartMessage;
import server.tools.BackButtonHandler;
import similarity_models.class_vectors.WIPOSimilarityFinder;
import ui_models.attributes.charts.*;
import ui_models.attributes.classification.SimilarityGatherTechTagger;
import ui_models.engines.SimilarityEngine;
import ui_models.portfolios.attributes.*;
import util.Pair;
import similarity_models.AbstractSimilarityModel;
import similarity_models.class_vectors.CPCSimilarityFinder;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import dl4j_neural_nets.tools.MyPreprocessor;
import j2html.tags.Tag;

import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import seeding.Constants;
import seeding.Database;
import similarity_models.sim_rank.SimRankSimilarityModel;
import ui_models.attributes.AbstractAttribute;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.value.ValueAttr;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.attributes.value.*;
import ui_models.filters.*;
import ui_models.portfolios.items.Item;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Session;
import ui_models.portfolios.PortfolioList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static ClassificationAttr tagger;
    public static final String PATENTS_TO_SEARCH_IN_FIELD = "patentsToSearchIn";
    public static final String ASSIGNEES_TO_SEARCH_IN_FIELD = "assigneesToSearchIn";
    public static final String PATENTS_TO_SEARCH_FOR_FIELD = "patentsToSearchFor";
    public static final String ASSIGNEES_TO_SEARCH_FOR_FIELD = "assigneesToSearchFor";
    public static final String TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD = "technologiesToSearchFor[]";
    public static final String TECHNOLOGIES_TO_FILTER_ARRAY_FIELD = "technologiesToFilter[]";
    public static final String VALUE_MODELS_ARRAY_FIELD = "valueModels[]";
    public static final String PRE_FILTER_ARRAY_FIELD = "preFilters[]";
    public static final String POST_FILTER_ARRAY_FIELD = "postFilters[]";
    public static final String ATTRIBUTES_ARRAY_FIELD = "attributes[]";
    public static final String LIMIT_FIELD = "limit";
    public static final String SIMILARITY_MODEL_FIELD = "similarityModel";
    public static final String COMPARATOR_FIELD = "comparator";
    public static final String SEARCH_TYPE_FIELD = "searchType";
    public static final String CHART_MODELS_ARRAY_FIELD = "chartModels[]";
    public static final String REPORT_URL = "/patent_recommendation_engine";
    public static final String WIPO_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD = "wipoTechnologiesToFilter[]";
    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    public static Map<String,ValueAttr> valueModelMap = new HashMap<>();
    public static Map<String,AbstractSimilarityModel> similarityModelMap = new HashMap<>();
    public static Map<String,AbstractFilter> preFilterModelMap = new HashMap<>();
    static Map<String,AbstractFilter> postFilterModelMap = new HashMap<>();
    static Map<String,AbstractAttribute> attributesMap = new HashMap<>();
    static Map<String,ChartAttribute> chartModelMap = new HashMap<>();
    static SimilarityEngine similarityEngine = new SimilarityEngine();

    protected static Map<String,String> humanAttrToJavaAttrMap;
    protected static Map<String,String> javaAttrToHumanAttrMap;
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        { // Attrs
            humanAttrToJavaAttrMap = new HashMap<>();
            humanAttrToJavaAttrMap.put("Asset", Constants.NAME);
            humanAttrToJavaAttrMap.put("Similarity", Constants.SIMILARITY);
            humanAttrToJavaAttrMap.put("Total Asset Count", Constants.TOTAL_ASSET_COUNT);
            humanAttrToJavaAttrMap.put("Assignee", Constants.ASSIGNEE);
            humanAttrToJavaAttrMap.put("Invention Title", Constants.INVENTION_TITLE);
            humanAttrToJavaAttrMap.put("AI Value", Constants.AI_VALUE);
            humanAttrToJavaAttrMap.put("Technology", Constants.TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Assignee Entity Type", Constants.ASSIGNEE_ENTITY_TYPE);
            humanAttrToJavaAttrMap.put("Large Portfolio Size", Constants.LARGE_PORTFOLIO_VALUE);
            humanAttrToJavaAttrMap.put("Small Portfolio Size", Constants.SMALL_PORTFOLIO_VALUE);
            humanAttrToJavaAttrMap.put("CompDB Assets Sold", Constants.COMPDB_ASSETS_SOLD_VALUE);
            humanAttrToJavaAttrMap.put("CompDB Assets Purchased", Constants.COMPDB_ASSETS_PURCHASED_VALUE);
            humanAttrToJavaAttrMap.put("Portfolio Size Greater Than", Constants.PORTFOLIO_SIZE_MINIMUM_FILTER);
            humanAttrToJavaAttrMap.put("Portfolio Size Smaller Than", Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER);
            humanAttrToJavaAttrMap.put("Similarity Threshold",Constants.SIMILARITY_THRESHOLD_FILTER);
            humanAttrToJavaAttrMap.put("AI Value Threshold",Constants.VALUE_THRESHOLD_FILTER);
            humanAttrToJavaAttrMap.put("Remove Non-Japanese Assignees Filter",Constants.JAPANESE_ONLY_FILTER);
            humanAttrToJavaAttrMap.put("Remove Japanese Assignees Filter",Constants.NO_JAPANESE_FILTER);
            humanAttrToJavaAttrMap.put("Remove Expired Assets Filter", Constants.EXPIRATION_FILTER);
            humanAttrToJavaAttrMap.put("Remove Assignees Filter", Constants.ASSIGNEES_TO_REMOVE_FILTER);
            humanAttrToJavaAttrMap.put("Remove Assets Filter", Constants.LABEL_FILTER);
            humanAttrToJavaAttrMap.put("Portfolio Size", Constants.PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Pie Chart", Constants.PIE_CHART);
            humanAttrToJavaAttrMap.put("Histogram",Constants.HISTOGRAM);
            humanAttrToJavaAttrMap.put("Search Scope", Constants.SEARCH_SCOPE_FILTER);
            humanAttrToJavaAttrMap.put("WIPO Technology",Constants.WIPO_TECHNOLOGY);

            // inverted version to get human readables back
            javaAttrToHumanAttrMap = new HashMap<>();
            humanAttrToJavaAttrMap.forEach((k, v) -> javaAttrToHumanAttrMap.put(v, k));

        }
    }

    public static String humanAttributeFor(String attr) {
        if(javaAttrToHumanAttrMap.containsKey(attr))  {
            return javaAttrToHumanAttrMap.get(attr);
        } else {
            return attr;
        }
    }

    public static void initialize() {
        loadAttributes();
        loadSimilarityModels();
        loadValueModels();
        loadFilterModels();
        loadTechTaggerModel();
        loadChartModels();
    }

    public static void loadChartModels() {
        chartModelMap.put(Constants.PIE_CHART, new AbstractDistributionChart());
        chartModelMap.put(Constants.HISTOGRAM, new AbstractHistogramChart());
    }

    public static void loadValueModels() {
        if(valueModelMap.isEmpty()) {
            try {
                valueModelMap.put(Constants.AI_VALUE, new OverallEvaluator());
                valueModelMap.put(Constants.COMPDB_ASSETS_PURCHASED_VALUE, new CompDBAssetsPurchasedEvaluator());
                valueModelMap.put(Constants.COMPDB_ASSETS_SOLD_VALUE, new CompDBAssetsSoldEvaluator());
                valueModelMap.put(Constants.LARGE_PORTFOLIO_VALUE, new PortfolioSizeEvaluator());
                valueModelMap.put(Constants.SMALL_PORTFOLIO_VALUE, new SmallPortfolioSizeEvaluator());
                valueModelMap.put(Constants.SIMILARITY, similarityEngine);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadFilterModels() {
        if(preFilterModelMap.isEmpty()&&postFilterModelMap.isEmpty()) {
            try {
                // Pre filters
                preFilterModelMap.put(Constants.SIMILARITY_THRESHOLD_FILTER,new SimilarityThresholdFilter());
                // Post filters
                postFilterModelMap.put(Constants.LABEL_FILTER,new LabelFilter());
                postFilterModelMap.put(Constants.VALUE_THRESHOLD_FILTER,new ValueThresholdFilter());
                postFilterModelMap.put(Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER,new PortfolioSizeMaximumFilter());
                postFilterModelMap.put(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER,new PortfolioSizeMinimumFilter());
                postFilterModelMap.put(Constants.EXPIRATION_FILTER,new ExpirationFilter());
                postFilterModelMap.put(Constants.ASSIGNEES_TO_REMOVE_FILTER, new AssigneeFilter());
                postFilterModelMap.put(Constants.TECHNOLOGY,new TechnologyFilter());
                postFilterModelMap.put(Constants.SEARCH_SCOPE_FILTER,new SearchScopeFilter());

            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadSimilarityModels() {
        if(similarityModelMap.isEmpty()) {
            boolean test = false;
            try {
                ForkJoinPool pool = new ForkJoinPool();
                pool.execute(()->{
                    similarityModelMap.put(Constants.PARAGRAPH_VECTOR_MODEL+"_patents",new SimilarPatentFinder(Database.getValuablePatents(), "** Paragraph Vector Model **"));
                    similarityModelMap.put(Constants.PARAGRAPH_VECTOR_MODEL+"_assignees", new SimilarPatentFinder(Database.getAssignees(), "** Paragraph Vector Model **"));
                });
                if(!test) {
                    pool.execute(() -> similarityModelMap.put(Constants.SIM_RANK_MODEL + "_patents", new SimRankSimilarityModel(Database.getValuablePatents(), "** SimRank Model **")));
                    pool.execute(() -> {
                        similarityModelMap.put(Constants.CPC_MODEL + "_patents", new CPCSimilarityFinder(Database.getValuablePatents(), "** CPC Model **"));
                        similarityModelMap.put(Constants.CPC_MODEL + "_assignees", new CPCSimilarityFinder(Database.getAssignees(), "** CPC Model **"));
                    });
                    pool.execute(() -> {
                        similarityModelMap.put(Constants.WIPO_MODEL + "_patents", new WIPOSimilarityFinder(Database.getValuablePatents(), "** WIPO Model **"));
                        similarityModelMap.put(Constants.WIPO_MODEL + "_assignees", new WIPOSimilarityFinder(Database.getAssignees(), "** WIPO Model **"));
                    });
                }
                pool.shutdown();
                pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadAttributes() {
        if(attributesMap.isEmpty()) {
            attributesMap.put(Constants.INVENTION_TITLE, new InventionTitleAttribute());
            attributesMap.put(Constants.ASSIGNEE, new AssigneeNameAttribute());
            attributesMap.put(Constants.PORTFOLIO_SIZE, new PortfolioSizeAttribute());
            attributesMap.put(Constants.TECHNOLOGY, new TechnologyAttribute(getTechTagger()));
            attributesMap.put(Constants.NAME, new NameAttribute());
            attributesMap.put(Constants.WIPO_TECHNOLOGY, new WIPOClassificationAttribute());
            loadValueModels();
        }
    }

    public static void loadTechTaggerModel() {
        if(tagger==null)tagger = TechTaggerNormalizer.getDefaultTechTagger();
    }

    public static ClassificationAttr getTechTagger() {
        if(tagger==null) loadTechTaggerModel();
        return tagger;
    }

    static void evaluateModel(AbstractAttribute model, List<Item> portfolio) {
        System.out.println("Starting to evaluate model: "+model.getName());
        PortfolioList portfolioList = new PortfolioList(portfolio);
        portfolioList.applyAttributes(Arrays.asList(model));
        System.out.println("Finished "+model.getName());
    }

    static String getAndRemoveMessage(Session session) {
        String message = session.attribute("message");
        if(message!=null)session.removeAttribute("message");
        return message;
    }

    static void hostPublicAssets() {
        File dir = new File("public/");
        hostAssetsHelper(dir,"");
    }

    private static void hostAssetsHelper(File file, String path) {
        if(file.isDirectory()) {
            for(File child : file.listFiles()) {
                hostAssetsHelper(child, path+"/"+child.getName());
            }
        } else {
            System.out.println("HOSTING ASSETS AT URL: "+path);
            get(path,(request, response) -> {
                String pathToFile = "public"+path;
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
        }
    }

    public static Collection<? extends AbstractAttribute> getAttributesFromPrerequisites(Collection<? extends DependentAttribute> dependentAttributes, Collection<String> appliedAttributes) {
        return dependentAttributes.stream().flatMap(dependency ->(Stream<String>)dependency.getPrerequisites().stream()).distinct().filter(preReq -> !appliedAttributes.contains(preReq)).map(preReq -> {
            if (attributesMap.containsKey(preReq)) {
                appliedAttributes.add(preReq);
                return attributesMap.get(preReq);
            }
            if (valueModelMap.containsKey(preReq)) {
                appliedAttributes.add(preReq);
                return valueModelMap.get(preReq);
            }
            return null;
        }).filter(model -> model != null && !(model instanceof DoNothing)).collect(Collectors.toList());
    }

    public static void applyTechnologyAttributes(Collection<String> technologies, PortfolioList portfolioList) {
        System.out.println("Applying technology values...");
        // Apply technology values
        List<SpecificTechnologyEvaluator> technologyEvaluators = new ArrayList<>();
        technologies.forEach(technology->{
            SpecificTechnologyEvaluator evaluator = new SpecificTechnologyEvaluator(technology,getTechTagger());
            technologyEvaluators.add(evaluator);
        });
        portfolioList.applyAttributes(technologyEvaluators);
    }

    public static void server() {
        port(4568);

        // HOST ASSETS
        hostPublicAssets();

        // GET METHODS
        get("/", (req, res) -> templateWrapper(res, div().with(candidateSetModelsForm()), getAndRemoveMessage(req.session())));

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

        post(REPORT_URL, (req, res) -> {
            res.type("application/json");
            try {
                System.out.println("Handling back button handler...");
                // handle navigation
                BackButtonHandler<String> navigator;
                if (req.session().attribute("navigator") == null) {
                    navigator = new BackButtonHandler<>();
                    req.session().attribute("navigator", navigator);
                } else {
                    navigator = req.session().attribute("navigator");
                }

                if (SimilarPatentServer.extractBool(req.queryMap(), "goBack")) {
                    String tmp = navigator.goBack();
                    if (tmp == null) return new Gson().toJson(new AjaxChartMessage("Unable to go back",Collections.emptyList()));
                    return tmp;
                } else if (SimilarPatentServer.extractBool(req.queryMap(), "goForward")) {
                    String tmp = navigator.goForward();
                    if (tmp == null) return new Gson().toJson(new AjaxChartMessage("Unable to go forward",Collections.emptyList()));
                    return tmp;
                }

                System.out.println("Getting parameters...");
                // get meta parameters
                int limit = extractInt(req, LIMIT_FIELD, 10);
                String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);


                System.out.println("Getting models...");
                // Get Models to use
                List<String> valueModels = extractArray(req, VALUE_MODELS_ARRAY_FIELD);
                List<String> preFilterModels = extractArray(req, PRE_FILTER_ARRAY_FIELD);
                List<String> postFilterModels = extractArray(req, POST_FILTER_ARRAY_FIELD);
                List<String> itemAttributes = extractArray(req, ATTRIBUTES_ARRAY_FIELD);
                List<String> chartModels = extractArray(req, CHART_MODELS_ARRAY_FIELD);
                List<String> technologies = extractArray(req, TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD);

                System.out.println(" ... Attributes");
                // Get data attributes
                List<AbstractAttribute> attributes = itemAttributes.stream().filter(attr -> !(attributesMap.get(attr) instanceof DoNothing)).map(attr -> attributesMap.get(attr)).collect(Collectors.toList());

                System.out.println(" ... Filters");
                // Get filters
                List<AbstractFilter> postFilters = postFilterModels.stream().map(modelName -> postFilterModelMap.get(modelName)).collect(Collectors.toList());
                List<AbstractFilter> preFilters = preFilterModels.stream().map(modelName -> preFilterModelMap.get(modelName)).collect(Collectors.toList());
                // Update filters based on params
                preFilters.stream().forEach(filter -> filter.extractRelevantInformationFromParams(req));
                postFilters.stream().forEach(filter -> filter.extractRelevantInformationFromParams(req));

                System.out.println(" ... Evaluators");
                // Get value models
                List<ValueAttr> evaluators = valueModels.stream().filter(modelName->!(valueModelMap.get(modelName) instanceof DoNothing)).map(modelName -> valueModelMap.get(modelName)).collect(Collectors.toList());

                Set<String> appliedAttributes = new HashSet<>();
                similarityEngine.extractRelevantInformationFromParams(req);
                PortfolioList portfolioList = similarityEngine.getPortfolioList();
                if(similarityEngine.wasEvaluated()) appliedAttributes.add(Constants.SIMILARITY);

                // handle comparator attributes and initialization of portfolio
                if (!appliedAttributes.contains(comparator)) {
                    System.out.println("Applying comparator field before sorting: " + comparator);
                    List<AbstractAttribute> attrList = new ArrayList<>();
                    if (attributesMap.containsKey(comparator)) {
                        AbstractAttribute attr = attributesMap.get(comparator);
                        attrList.add(attr);
                    } else if (valueModelMap.containsKey(comparator)) {
                        AbstractAttribute attr = valueModelMap.get(comparator);
                        attrList.add(attr);
                    }
                    if(attrList.size()>0)portfolioList.applyAttributes(attrList);
                }
                portfolioList.init(comparator, limit);

                System.out.println("Applying necessary prerequisite attributes for post filters...");
                // Add necessary attributes for post filters
                portfolioList.applyAttributes(getAttributesFromPrerequisites(postFilters,appliedAttributes));

                System.out.println("Applying post filters...");
                // Run filters
                portfolioList.applyFilters(postFilters);

                // Apply technology data
                System.out.println("Applying technologies...");
                if (technologies.size() > 0) {
                    applyTechnologyAttributes(technologies, portfolioList);
                }

                // Apply attributes
                System.out.println("Applying attributes...");
                portfolioList.applyAttributes(attributes.stream().filter(attr->!appliedAttributes.contains(attr.getName())).collect(Collectors.toList()));
                appliedAttributes.addAll(itemAttributes);

                // Apply value models
                System.out.println("Applying value models...");
                portfolioList.applyAttributes(evaluators.stream().filter(attr->!appliedAttributes.contains(attr.getName())).collect(Collectors.toList()));
                appliedAttributes.addAll(valueModels);

                List<ChartAttribute> charts = chartModels.stream().map(chart->chartModelMap.get(chart)).collect(Collectors.toList());
                charts.forEach(chart->chart.extractRelevantInformationFromParams(req));
                System.out.println("Applying pre chart attributes...");
                portfolioList.applyAttributes(getAttributesFromPrerequisites(charts,appliedAttributes));

                // reapply filters just in case
                List<AbstractFilter> allFilters = Arrays.asList(preFilters,postFilters).stream().flatMap(list->list.stream()).collect(Collectors.toList());
                portfolioList.applyFilters(allFilters);

                List<AbstractChart> finishedCharts = new ArrayList<>();
                // adding charts
                charts.forEach(chartModel->{
                    finishedCharts.add(chartModel.create(portfolioList));
                });

                System.out.println("Rendering table...");
                AtomicInteger chartCnt = new AtomicInteger(0);
                String html = new Gson().toJson(new AjaxChartMessage(div().with(
                        finishedCharts.isEmpty() ? div() : div().with(
                                h4("Charts"),
                                div().with(
                                        charts.stream().map(c -> div().attr("style","margin-bottom: 20px;").withId("chart-" + chartCnt.getAndIncrement())).collect(Collectors.toList())
                                )
                        ),
                        portfolioList == null ? div() : div().with(
                                h4("Data"),
                                tableFromPatentList(portfolioList.getItemList(), Arrays.asList(itemAttributes, valueModels, technologies.stream().map(tech -> tech + SpecificTechnologyEvaluator.TECHNOLOGY_SUFFIX).collect(Collectors.toList())).stream().flatMap(list -> list.stream()).collect(Collectors.toList()))
                        )
                ).render(), finishedCharts));

                navigator.addRequest(html);

                return html;

            } catch (Exception e) {
                System.out.println(e.getClass().getName() + ": " + e.getMessage());
                return new Gson().toJson(new AjaxChartMessage("ERROR: " + e.getMessage(), Collections.emptyList()));
            }
        });


    }

    static String ajaxSubmitWithChartsScript(String ID, String url, String buttonText, String buttonTextWhileSearching) {
        return "$('#"+ID+"-button').attr('disabled',true).text('"+buttonTextWhileSearching+"...');"
                + "var url = '"+ url +"'; "
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

    static Tag navigationTag() {
        return div().attr("style","margin-top: -10px;").with(
                form().attr("onsubmit",ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID+"-back", REPORT_URL,"Back","Going back"))
                        .attr("style","float: left;").withId(GENERATE_REPORTS_FORM_ID+"-back").with(
                        input().withName("goBack").withValue("on").withType("hidden"), br(),
                        button("Back").withClass("button").withId(GENERATE_REPORTS_FORM_ID+"-back"+"-button").withType("submit")
                ),
                form().attr("onsubmit",ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID+"-forward", REPORT_URL,"Forward","Going forward"))
                        .attr("style","float: right;").withId(GENERATE_REPORTS_FORM_ID+"-forward").with(
                        input().withName("goForward").withValue("on").withType("hidden"), br(),
                        button("Forward").withClass("button").withId(GENERATE_REPORTS_FORM_ID+"-forward"+"-button").withType("submit")
                ));
    }

    static Tag tableFromPatentList(List<Item> items, List<String> attributes) {
        return table().with(
                thead().with(
                        tr().with(
                                attributes.stream().map(attr->th(humanAttributeFor(attr))).collect(Collectors.toList())
                        )
                ),tbody().with(
                        items.stream().map(item->tr().with(
                                item.getDataAsRow(attributes).stream().map(cell->cell==null?td(""):td(cell.toString())).collect(Collectors.toList())
                        )).collect(Collectors.toList())
                )

        );
    }


    public static PortfolioList runPatentFinderModel(AbstractSimilarityModel firstFinder, AbstractSimilarityModel secondFinder, int resultLimit, Collection<? extends AbstractFilter> preFilters) {
        try {
            return firstFinder.similarFromCandidateSet(secondFinder, resultLimit, preFilters);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("... while running patent finder model.");
            throw new RuntimeException(e.getMessage());
        }
    }

    public static List<String> preProcess(String toSplit, String delim, String toReplace) {
        if(toSplit==null||toSplit.trim().length()==0) return new ArrayList<>();
        return Arrays.asList(toSplit.split(delim)).stream().filter(str->str!=null).map(str->toReplace!=null&&toReplace.length()>0?str.trim().replaceAll(toReplace,""):str.trim()).filter(str->str!=null&&!str.isEmpty()).collect(Collectors.toList());
    }

    static Tag templateWrapper(Response res, Tag form, String message) {
        res.type("text/html");
        if(message==null)message="";
        return html().with(
                head().with(
                        script().attr("src","https://code.jquery.com/jquery-1.12.4.js"),
                        script().attr("src","https://code.jquery.com/ui/1.12.1/jquery-ui.js"),
                        script().attr("src","http://code.highcharts.com/highcharts.js"),
                        script().attr("src","/js/customEvents.js"),
                        script().attr("src","/js/multiselect.js"),
                        script().attr("src","/js/defaults.js"),
                        link().withRel("stylesheet").withHref("/css/multiselect.css"),
                        link().withRel("stylesheet").withHref("/css/defaults.css"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().with(
                        div().attr("style", "width:80%; padding: 2% 10%;").with(
                                a().attr("href", "/").with(
                                        img().attr("src", "/images/brand.png")
                                ),
                                hr(),
                                h2("Artificial Intelligence Platform"),
                                hr(),
                                h4(message),
                                form,
                                br(),
                                br(),
                                br()
                        )
                )
        );
    }


    static Tag expandableDiv(String label, Tag... innerStuff) {
        return expandableDiv(label,true,innerStuff);
    }

    static Tag expandableDiv(String label, boolean hiddenToStart, Tag... innerStuff) {
        String id = "div-"+label.hashCode();
        return div().with(label("Toggle "+label).attr("style","cursor: pointer; color: blue; text-decoration: underline;").attr("onclick","$('#"+id+"').toggle();"),
                div().withId(id).attr("style","display: "+(hiddenToStart?"none;":"block;")).with(
                        innerStuff
                )
        );
    }

    public static Tag gatherTechnologySelect(String name) {
        return technologySelect(name,getTechTagger().getClassifications().stream().sorted().collect(Collectors.toList()));
    }

    public static Tag technologySelect(String name, List<String> orderedClassifications) {
        String id = "checkboxes-"+name.hashCode();
        return div().withClass("multiselect").with(
                div().withClass("selectBox").attr("onclick","showCheckboxes('"+id+"');").with(
                        select().with(
                                option("Select an Option")
                        ),
                        div().withClass("overSelect")
                ), div().attr("style","max-height: 400px; overflow-y: scroll;").with(
                        div().withId(id).withClass("checkboxes").with(
                                orderedClassifications.stream().map(technology-> {
                                    return div().with(label(humanAttributeFor(technology)).with(input().withType("checkbox").attr("style","float: right;").withName(name).withValue(technology)));
                                }).collect(Collectors.toList())
                        )
                )
        );
    }

    private static Tag candidateSetModelsForm() {
        return div().with(
                navigationTag(),
                br(),
                br(),
                br(),
                form().withId(GENERATE_REPORTS_FORM_ID).attr("onsubmit", ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID, REPORT_URL,"Generate","Generating...")).with(
                        h1("Patent Recommendation Engine").attr("style","text-align: center;"),br(),
                        table().attr("style","width: 100%").with(
                                tbody().with(
                                        tr().attr("style","vertical-align: top;").with(
                                                td().attr("style","vertical-align: top; width: 100%;").with(
                                                        div().withClass("droppable options").with(
                                                                h4("Search Options"),
                                                                table().with(
                                                                        tbody().with(
                                                                                tr().attr("style","vertical-align: top;").with(
                                                                                        td().attr("style","width: 33%;").withClass("draggable").with(
                                                                                                label("Result Type"),br(),
                                                                                                select().withName(SEARCH_TYPE_FIELD).with(
                                                                                                        Arrays.stream(PortfolioList.Type.values()).map(type->{
                                                                                                            ContainerTag option = option(type.toString()).withValue(type.toString());
                                                                                                            if(type.equals(PortfolioList.Type.patents)) option=option.attr("selected","selected");
                                                                                                            return option;
                                                                                                        }).collect(Collectors.toList())
                                                                                                )
                                                                                        ),
                                                                                        td().attr("style","width: 33%;").withClass("draggable").with(
                                                                                                label("Sorted By"),br(),select().withName(COMPARATOR_FIELD).with(
                                                                                                        valueModelMap.keySet().stream().map(key-> {
                                                                                                            return option(humanAttributeFor(key)).withValue(key);
                                                                                                        }).collect(Collectors.toList())
                                                                                                )
                                                                                        ),
                                                                                        td().attr("style","width: 33%;").withClass("draggable").with(
                                                                                                label("Result Limit"),br(),input().withType("number").withValue("10").withName(LIMIT_FIELD)
                                                                                        )
                                                                                )
                                                                        )
                                                                )


                                                        ),br()
                                                )
                                        ), tr().attr("style","vertical-align: top;").with(
                                                td().attr("style","vertical-align: top; width: 100%;").with(
                                                        div().attr("style","width: 45%;").withClass("droppable values start").with(
                                                                h4("Available Values"),
                                                                div().with(
                                                                        valueModelMap.entrySet().stream().map(e-> {
                                                                            return div().withClass("draggable values ").with(
                                                                                    label(humanAttributeFor(e.getKey())),
                                                                                    input().attr("disabled","disabled").withType("checkbox").withClass("checkbox").withName(VALUE_MODELS_ARRAY_FIELD).withValue(e.getKey()),
                                                                                    div().withClass("toggle").with(e.getValue().getOptionsTag())
                                                                            );
                                                                        }).collect(Collectors.toList())
                                                                )
                                                        ),div().attr("style","width: 45%;").withClass("droppable values target").with(
                                                                h4("Values to Apply")
                                                        ), br()
                                                )
                                        ),tr().attr("style","vertical-align: top;").with(
                                                td().attr("style","vertical-align: top; width: 100%;").with(
                                                        div().attr("style","width: 45%;").withClass("droppable attributes start").with(
                                                                h4("Available Attributes"),
                                                                div().with(
                                                                        attributesMap.entrySet().stream().map(e-> {
                                                                            return div().withClass("draggable attributes").with(
                                                                                    label(humanAttributeFor(e.getKey())),
                                                                                    input().attr("disabled","disabled").withType("checkbox").withClass("checkbox").withName(ATTRIBUTES_ARRAY_FIELD).withValue(e.getKey()),
                                                                                    div().withClass("toggle").with(e.getValue().getOptionsTag())
                                                                            );
                                                                        }).collect(Collectors.toList())
                                                                )
                                                        ),div().attr("style","width: 45%;").withClass("droppable attributes target").with(
                                                                h4("Attributes to Apply")
                                                        ),br()
                                                )
                                        ), tr().attr("style","vertical-align: top;").with(
                                                td().attr("style","vertical-align: top; width: 100%;").with(
                                                        div().attr("style","width: 45%;").withClass("droppable filters start").with(
                                                                h4("Available Filters"),
                                                                div().with(
                                                                        Arrays.asList(new Pair<>(preFilterModelMap,PRE_FILTER_ARRAY_FIELD),new Pair<>(postFilterModelMap,POST_FILTER_ARRAY_FIELD)).stream().flatMap(pair-> {
                                                                            return pair._1.entrySet().stream().map(e->{
                                                                                return div().withClass("draggable filters").with(
                                                                                        label(humanAttributeFor(e.getKey())),
                                                                                        input().attr("disabled","disabled").withType("checkbox").withClass("checkbox").withName(pair._2).withValue(e.getKey()),
                                                                                        div().withClass("toggle").with(e.getValue().getOptionsTag())
                                                                                );
                                                                            });
                                                                        }).collect(Collectors.toList())
                                                                )

                                                        ), div().attr("style","width: 45%;").withClass("droppable filters target").with(
                                                                h4("Filters to Apply")
                                                        ), br()
                                                )
                                        ), tr().attr("style","vertical-align: top;").with(
                                                td().attr("style","vertical-align: top; width: 100%;").with(
                                                        div().attr("style","width: 45%;").withClass("droppable charts start").with(
                                                                h4("Available Charts"),
                                                                div().with(
                                                                        chartModelMap.entrySet().stream().map(e->{
                                                                            return div().withClass("draggable charts").with(
                                                                                    label(humanAttributeFor(e.getKey())),
                                                                                    input().attr("disabled","disabled").withType("checkbox").withClass("checkbox").withName(CHART_MODELS_ARRAY_FIELD).withValue(e.getKey()),
                                                                                    div().withClass("toggle").with(e.getValue().getOptionsTag())
                                                                            );
                                                                        }).collect(Collectors.toList())
                                                                )
                                                        ), div().attr("style","width: 45%;").withClass("droppable charts target").with(
                                                                h4("Charts to Apply")
                                                        ), br()
                                                )
                                        ), tr().with(
                                                td().attr("style","width: 100%;").with(
                                                        br(),br(),
                                                        button("Generate").withClass("button").withId(GENERATE_REPORTS_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                )
                        )

                ), hr(), div().withId("results")
        );
    }

    public static List<String> extractArray(Request req, String param) {
        try {
            String[] array = req.queryParamsValues(param);
            if (array != null) {
                List<String> list = Arrays.stream(array).collect(Collectors.toList());
                return list;
            }
            else return Collections.emptyList();
        } catch(Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    public static String extractString(Request req, String param, String defaultVal) {
        return extractString(req.queryMap(),param, defaultVal);
    }
    public static String extractString(QueryParamsMap paramsMap, String param, String defaultVal) {
        if(paramsMap.value(param)!=null&&paramsMap.value(param).trim().length()>0) {
            return paramsMap.value(param);
        } else {
            return defaultVal;
        }
    }
    public static int extractInt(Request req, String param, int defaultVal) {
        return extractInt(req.queryMap(),param, defaultVal);
    }
    static int extractInt(QueryParamsMap req, String param, int defaultVal) {
        try {
            return Integer.valueOf(req.value(param));
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return defaultVal;
        }
    }
    static double extractDouble(Request req, String param, double defaultVal) {
        return extractDouble(req.queryMap(),param, defaultVal);
    }
    static double extractDouble(QueryParamsMap queryMap, String param, double defaultVal) {
        try {
            return Double.valueOf(queryMap.value(param));
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return defaultVal;
        }
    }

    static boolean extractBool(Request req, String param) {
        return extractBool(req.queryMap(),param);
    }
    static boolean extractBool(QueryParamsMap req, String param) {
        try {
            return (req.value(param)==null||!req.value(param).startsWith("on")) ? false : true;
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        //Database.setupSeedConn();
        Database.initializeDatabase();
        System.out.println("Starting to load base finder...");
        initialize();
        System.out.println("Finished loading base finder.");
        System.out.println("Starting server...");
        server();
        System.out.println("Finished starting server.");
    }
}
