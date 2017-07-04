package server;

import com.google.gson.Gson;
import highcharts.AbstractChart;
import j2html.tags.ContainerTag;
import server.tools.AjaxChartMessage;
import server.tools.BackButtonHandler;
import similarity_models.class_vectors.WIPOSimilarityFinder;
import ui_models.attributes.charts.*;
import ui_models.engines.*;
import ui_models.excel.ExcelHandler;
import ui_models.exceptions.AttributeException;
import ui_models.portfolios.attributes.*;
import ui_models.templates.*;
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
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    public static final String SIMILARITY_ENGINES_ARRAY_FIELD = "similarityEngines[]";
    public static final String PRE_FILTER_ARRAY_FIELD = "preFilters[]";
    public static final String SIMILARITY_FILTER_ARRAY_FIELD = "similarityFilters[]";
    public static final String POST_FILTER_ARRAY_FIELD = "postFilters[]";
    public static final String ATTRIBUTES_ARRAY_FIELD = "attributes[]";
    public static final String LIMIT_FIELD = "limit";
    public static final String SIMILARITY_MODEL_FIELD = "similarityModel";
    public static final String COMPARATOR_FIELD = "comparator";
    public static final String SEARCH_TYPE_FIELD = "searchType";
    public static final String CHART_MODELS_ARRAY_FIELD = "chartModels[]";
    public static final String REPORT_URL = "/patent_recommendation_engine";
    public static final String DOWNLOAD_URL = "/excel_generation";
    public static final String WIPO_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD = "wipoTechnologiesToFilter[]";
    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    public static Map<String,AbstractSimilarityModel> similarityModelMap = new HashMap<>();
    public static SimilarityEngine similarityEngine;
    public static Map<String,AbstractFilter> preFilterModelMap = new HashMap<>();
    public static Map<String,AbstractFilter> postFilterModelMap = new HashMap<>();
    public static Map<String,AbstractFilter> similarityFilterModelMap = new HashMap<>();
    static Map<String,AbstractAttribute> attributesMap = new HashMap<>();
    static Map<String,ChartAttribute> chartModelMap = new HashMap<>();
    static List<FormTemplate> templates = new ArrayList<>();
    static List<Item> allPatents = Collections.synchronizedList(new ArrayList<>());
    static List<Item> allAssignees = Collections.synchronizedList(new ArrayList<>());
    static Map<String,Item> nameToItemMap = Collections.synchronizedMap(new HashMap<>());
    static Collection<? extends AbstractAttribute> preComputedAttributes;

    protected static Map<String,String> humanAttrToJavaAttrMap;
    protected static Map<String,String> javaAttrToHumanAttrMap;
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        { // Attrs
            humanAttrToJavaAttrMap = new HashMap<>();
            humanAttrToJavaAttrMap.put("Patent Number", Constants.NAME);
            humanAttrToJavaAttrMap.put("Similarity", Constants.SIMILARITY);
            humanAttrToJavaAttrMap.put("Technology Similarity", Constants.TECHNOLOGY_SIMILARITY);
            humanAttrToJavaAttrMap.put("Assignee Similarity", Constants.ASSIGNEE_SIMILARITY);
            humanAttrToJavaAttrMap.put("Patent Similarity", Constants.PATENT_SIMILARITY);
            humanAttrToJavaAttrMap.put("Total Asset Count", Constants.TOTAL_ASSET_COUNT);
            humanAttrToJavaAttrMap.put("Assignee", Constants.ASSIGNEE);
            humanAttrToJavaAttrMap.put("Invention Title", Constants.INVENTION_TITLE);
            humanAttrToJavaAttrMap.put("AI Value", Constants.AI_VALUE);
            humanAttrToJavaAttrMap.put("Technology", Constants.TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Assignee Entity Type", Constants.ASSIGNEE_ENTITY_TYPE);
            humanAttrToJavaAttrMap.put("CompDB Assets Sold", Constants.COMPDB_ASSETS_SOLD);
            humanAttrToJavaAttrMap.put("CompDB Assets Purchased", Constants.COMPDB_ASSETS_PURCHASED);
            humanAttrToJavaAttrMap.put("Portfolio Size Greater Than", Constants.PORTFOLIO_SIZE_MINIMUM_FILTER);
            humanAttrToJavaAttrMap.put("Portfolio Size Smaller Than", Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER);
            humanAttrToJavaAttrMap.put("Similarity Threshold",Constants.SIMILARITY_THRESHOLD_FILTER);
            humanAttrToJavaAttrMap.put("AI Value Threshold",Constants.VALUE_THRESHOLD_FILTER);
            humanAttrToJavaAttrMap.put("Only Include Japanese",Constants.JAPANESE_ONLY_FILTER);
            humanAttrToJavaAttrMap.put("Remove Japanese",Constants.NO_JAPANESE_FILTER);
            humanAttrToJavaAttrMap.put("Remove Expired Assets", Constants.EXPIRATION_FILTER);
            humanAttrToJavaAttrMap.put("Remove Assignees", Constants.ASSIGNEES_TO_REMOVE_FILTER);
            humanAttrToJavaAttrMap.put("Remove Patents", Constants.LABEL_FILTER);
            humanAttrToJavaAttrMap.put("Portfolio Size", Constants.PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Patents",PortfolioList.Type.patents.toString());
            humanAttrToJavaAttrMap.put("Assignees",PortfolioList.Type.assignees.toString());
            humanAttrToJavaAttrMap.put("Pie Chart", Constants.PIE_CHART);
            humanAttrToJavaAttrMap.put("Histogram",Constants.HISTOGRAM);
            humanAttrToJavaAttrMap.put("Patent Scope", Constants.PATENT_SEARCH_SCOPE_FILTER);
            humanAttrToJavaAttrMap.put("Assignee Scope", Constants.ASSIGNEE_SEARCH_SCOPE_FILTER);
            humanAttrToJavaAttrMap.put("WIPO Technology",Constants.WIPO_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Remaining Life (Years)",Constants.REMAINING_LIFE);
            humanAttrToJavaAttrMap.put("Minimum Remaining Life (Years)",Constants.REMAINING_LIFE_FILTER);

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
        loadFilterModels();
        loadTechTaggerModel();
        loadChartModels();
        loadTemplates();
        loadAllItems();
        loadSimilarityModels();
    }

    public static void loadChartModels() {
        chartModelMap.put(Constants.PIE_CHART, new AbstractDistributionChart());
        chartModelMap.put(Constants.HISTOGRAM, new AbstractHistogramChart());
    }


    public static void loadTemplates() {
        if(templates.isEmpty()) {
            templates.add(new PortfolioAssessment());
            templates.add(new SimilarPatentSearch());
            templates.add(new SimilarAssigneeSearch());
            templates.add(new LeadDevelopmentSearch());
            templates.add(new FormTemplate("Reset Form",new HashMap<>(), FormTemplate.similarityPatentSmall(),Collections.emptyList()));
        }
    }

    public static void loadFilterModels() {
        if(preFilterModelMap.isEmpty()&&postFilterModelMap.isEmpty()) {
            try {
                // Pre filters
                preFilterModelMap.put(Constants.LABEL_FILTER,new LabelFilter());
                preFilterModelMap.put(Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER,new PortfolioSizeMaximumFilter());
                preFilterModelMap.put(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER,new PortfolioSizeMinimumFilter());
                preFilterModelMap.put(Constants.ASSIGNEES_TO_REMOVE_FILTER, new AssigneeFilter());
                preFilterModelMap.put(Constants.NO_JAPANESE_FILTER, new RemoveJapaneseAssigneeFilter());
                preFilterModelMap.put(Constants.JAPANESE_ONLY_FILTER, new IncludeJapaneseAssigneeFilter());
                preFilterModelMap.put(Constants.VALUE_THRESHOLD_FILTER,new ValueThresholdFilter());
                preFilterModelMap.put(Constants.EXPIRATION_FILTER,new ExpirationFilter());
                preFilterModelMap.put(Constants.PATENT_SEARCH_SCOPE_FILTER,new PatentSearchScopeFilter());
                preFilterModelMap.put(Constants.ASSIGNEE_SEARCH_SCOPE_FILTER, new AssigneeSearchScopeFilter());
                preFilterModelMap.put(Constants.COMPDB_ASSETS_PURCHASED, new CompDBAssetsPurchasedFilter());
                preFilterModelMap.put(Constants.COMPDB_ASSETS_SOLD, new CompDBAssetsSoldFilter());
                preFilterModelMap.put(Constants.WIPO_TECHNOLOGY, new WIPOTechnologyFilter());
                preFilterModelMap.put(Constants.REMAINING_LIFE_FILTER, new RemainingLifeFilter());

                // During filters
                similarityFilterModelMap.put(Constants.SIMILARITY_THRESHOLD_FILTER,new SimilarityThresholdFilter());

                // Post filters
                postFilterModelMap.put(Constants.TECHNOLOGY,new TechnologyFilter());

                // pre computed attributes
                preComputedAttributes = getAttributesFromPrerequisites(preFilterModelMap.values(), new HashSet<>());
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadSimilarityModels() {
        loadAllItems();
        if(similarityModelMap.isEmpty()) {
            boolean test = true;
            try {
                ForkJoinPool pool = new ForkJoinPool();
                pool.execute(()->{
                    similarityModelMap.put(Constants.PARAGRAPH_VECTOR_MODEL+"_patents",new SimilarPatentFinder(allPatents, "** Paragraph Vector Model **"));
                    similarityModelMap.put(Constants.PARAGRAPH_VECTOR_MODEL+"_assignees", new SimilarPatentFinder(allAssignees, "** Paragraph Vector Model **"));
                });
                if(!test) {
                    pool.execute(() -> similarityModelMap.put(Constants.SIM_RANK_MODEL + "_patents", new SimRankSimilarityModel(allPatents, "** SimRank Model **")));
                    pool.execute(() -> {
                        similarityModelMap.put(Constants.CPC_MODEL + "_patents", new CPCSimilarityFinder(allPatents, "** CPC Model **"));
                        similarityModelMap.put(Constants.CPC_MODEL + "_assignees", new CPCSimilarityFinder(allAssignees, "** CPC Model **"));
                    });
                    pool.execute(() -> {
                        similarityModelMap.put(Constants.WIPO_MODEL + "_patents", new WIPOSimilarityFinder(allPatents, "** WIPO Model **"));
                        similarityModelMap.put(Constants.WIPO_MODEL + "_assignees", new WIPOSimilarityFinder(allAssignees, "** WIPO Model **"));
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
            attributesMap.put(Constants.COMPDB_ASSETS_PURCHASED, new CompDBAssetsPurchasedAttribute());
            attributesMap.put(Constants.COMPDB_ASSETS_SOLD, new CompDBAssetsSoldAttribute());
            attributesMap.put(Constants.AI_VALUE, new OverallEvaluator());
            attributesMap.put(Constants.REMAINING_LIFE, new RemainingLifeAttribute());

            // similarity engine
            similarityEngine = new SimilarityEngine(Arrays.asList(new PatentSimilarityEngine(),new AssigneeSimilarityEngine(), new TechnologySimilarityEngine()));
        }
    }

    public static void loadAllItems() {
        if(allPatents.isEmpty()) {
            Database.getCopyOfAllPatents().parallelStream().forEach(patent -> {
                Item item = new Item(patent);
                preComputedAttributes.forEach(model -> {
                    item.addData(model.getName(), model.attributesFor(Arrays.asList(item.getName()), 1));
                });
                allPatents.add(item);
                nameToItemMap.put(patent,item);
            });
        }

        if(allAssignees.isEmpty()) {
            Database.getAssignees().parallelStream().forEach(assignee -> {
                Item item = new Item(assignee);
                preComputedAttributes.forEach(model -> {
                    item.addData(model.getName(), model.attributesFor(Arrays.asList(item.getName()), 1));
                });
                allAssignees.add(item);
                nameToItemMap.put(assignee,item);
            });
        }
    }

    public static Item[] findItemsByName(Collection<String> names) {
        return names.stream().map(name->nameToItemMap.get(name)).filter(item->item!=null).toArray(size->new Item[size]);
    }

    public static void loadTechTaggerModel() {
        if(tagger==null)tagger = TechTaggerNormalizer.getDefaultTechTagger();
    }

    public static ClassificationAttr getTechTagger() {
        if(tagger==null) loadTechTaggerModel();
        return tagger;
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
            AbstractAttribute attr = attributesMap.get(preReq);
            if (attr != null) {
                appliedAttributes.add(preReq);
            }
            return attr;
        }).filter(model -> model != null).collect(Collectors.toList());
    }

    public static void applyTechnologyAttributes(Collection<String> technologies, PortfolioList portfolioList) throws AttributeException {
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
        get("/", (req, res) -> templateWrapper(res, candidateSetModelsForm()));

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

        post(REPORT_URL, (req, res) -> handleReport(req,res));

        post(DOWNLOAD_URL, (req, res) -> handleExcel(req,res));
    }

    private static Object handleExcel(Request req, Response res) {
        try {
            HttpServletResponse raw = res.raw();
            res.header("Content-Disposition", "attachment; filename=download.xls");
            res.type("application/force-download");
            Gson gson = new Gson();
            String json = extractString(req,"data","{}");
            System.out.println("JSON: "+json);
            Map<String,Object> map = new HashMap<>();
            map = (Map<String,Object>) gson.fromJson(json, map.getClass());
            System.out.println("Size of map: "+map.size());
            List<String> headers = (List<String>)map.getOrDefault("headers",Collections.emptyList());
            System.out.println("Number of excel headers: "+headers.size());
            List<List<String>> data = (List<List<String>>)map.getOrDefault("rows",Collections.emptyList());
            ExcelHandler.writeDefaultSpreadSheetToRaw(raw, "data", "Data", data,  headers);
            return raw;
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return new Gson().toJson(new AjaxChartMessage("ERROR "+e.getClass().getName()+": " + e.getMessage(), Collections.emptyList()));
        }
    }


    private static Object handleReport(Request req, Response res) {
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
            int maxResultLimit = 10000;
            if(limit > maxResultLimit) {
                return new Gson().toJson(new AjaxChartMessage("Error: Maximum result limit is "+maxResultLimit+ " which is less than "+limit,Collections.emptyList()));
            }

            System.out.println("Getting models...");
            // Get Models to use
            List<String> preFilterModels = extractArray(req, PRE_FILTER_ARRAY_FIELD);
            List<String> similarityFilterModels = extractArray(req, SIMILARITY_FILTER_ARRAY_FIELD);
            List<String> postFilterModels = extractArray(req, POST_FILTER_ARRAY_FIELD);
            List<String> itemAttributes = extractArray(req, ATTRIBUTES_ARRAY_FIELD);
            List<String> chartModels = extractArray(req, CHART_MODELS_ARRAY_FIELD);
            List<String> similarityEngines = extractArray(req, SIMILARITY_ENGINES_ARRAY_FIELD);

            System.out.println(" ... Attributes");
            // Get data attributes
            List<AbstractAttribute> attributes = itemAttributes.stream().map(attr -> attributesMap.get(attr)).collect(Collectors.toList());

            System.out.println(" ... Filters");
            // Get filters
            List<AbstractFilter> postFilters = postFilterModels.stream().map(modelName -> postFilterModelMap.get(modelName)).collect(Collectors.toList());
            List<AbstractFilter> preFilters = preFilterModels.stream().map(modelName -> preFilterModelMap.get(modelName)).collect(Collectors.toList());
            List<AbstractFilter> similarityFilters = similarityFilterModels.stream().map(modelName -> similarityFilterModelMap.get(modelName)).collect(Collectors.toList());

            // Update filters based on params
            postFilters.stream().forEach(filter -> filter.extractRelevantInformationFromParams(req));
            postFilters=postFilters.stream().filter(filter->filter.isActive()).collect(Collectors.toList());
            similarityFilters.stream().forEach(filter -> filter.extractRelevantInformationFromParams(req));

            System.out.println(" ... Evaluators");
            // Get value models

            Set<String> appliedAttributes = new HashSet<>(preComputedAttributes.stream().map(model->model.getName()).collect(Collectors.toList()));
            // get portfolio list from similarity engine
            similarityEngine.extractRelevantInformationFromParams(req);
            PortfolioList portfolioList = similarityEngine.getPortfolioList();

            // Run filters
            if(postFilters.size()>0) {
                System.out.println("Applying necessary prerequisite attributes for post filters...");
                portfolioList.applyAttributes(getAttributesFromPrerequisites(postFilters,appliedAttributes));
                System.out.println("Applying post filters...");
                portfolioList.applyFilters(postFilters);
            }

            // Apply attributes
            System.out.println("Applying attributes...");
            portfolioList.applyAttributes(attributes.stream().filter(attr->!appliedAttributes.contains(attr.getName())).collect(Collectors.toList()));
            appliedAttributes.addAll(itemAttributes);

            List<String> tableHeaders = new ArrayList<>(itemAttributes);
            if(similarityEngines.size()>0) {
                tableHeaders.add(Math.min(tableHeaders.size(),1),Constants.SIMILARITY);
            }


            res.type("application/json");
            List<ChartAttribute> charts = chartModels.stream().map(chart->chartModelMap.get(chart)).collect(Collectors.toList());
            charts.forEach(chart->chart.extractRelevantInformationFromParams(req));
            System.out.println("Applying pre chart attributes...");
            portfolioList.applyAttributes(getAttributesFromPrerequisites(charts,appliedAttributes));

            List<AbstractChart> finishedCharts = new ArrayList<>();
            // adding charts
            charts.forEach(chartModel->{
                finishedCharts.addAll(chartModel.create(portfolioList));
            });

            System.out.println("Rendering table...");
            AtomicInteger chartCnt = new AtomicInteger(0);
            String tableId = "portfolio-list-data-table";
            String html = new Gson().toJson(new AjaxChartMessage(div().with(
                    finishedCharts.isEmpty() ? div() : div().withClass("row").attr("style","margin-bottom: 10px;").with(
                            h4("Charts").withClass("collapsible-header").attr("data-target","#data-charts"),
                            span().withId("data-charts").withClass("collapse show").with(
                                    finishedCharts.stream().map(c -> div().attr("style","width: 80%; margin-left: 10%;").withId("chart-" + chartCnt.getAndIncrement())).collect(Collectors.toList())
                            ),br()
                    ),portfolioList == null ? div() : div().withClass("row").attr("style","margin-top: 10px;").with(
                            h4("Data").withClass("collapsible-header").attr("data-target","#data-table"),
                            tableFromPatentList(portfolioList.getItemList(), tableHeaders, tableId, DOWNLOAD_URL)
                    )
            ).render(), finishedCharts));

            navigator.addRequest(html);
            return html;
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return new Gson().toJson(new AjaxChartMessage("ERROR "+e.getClass().getName()+": " + e.getMessage(), Collections.emptyList()));
        }
    }

    static String ajaxSubmitWithChartsScript(String ID, String url, String buttonText, String buttonTextWhileSearching) {
        return "$('#"+ID+"-button').attr('disabled',true).text('"+buttonTextWhileSearching+"');"
                + "var url = '"+ url +"'; "
                + "var tempScrollTop = $(window).scrollTop();"
                //+ "window.onerror = function(errorMsg, url, lineNumber) {"
                //+ "    $('#results').html(\"<div style='color:red;'>JavaScript error occured: \" + errorMsg + '</div>');"
                //+ "    $('#"+ID+"-button').attr('disabled',false).text('"+buttonText+"');"
                //+ "    return false;"
                //+ "};"
                + "$('#results').html('');       " // clears results div
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
                + "    setupDataTable($('#results #data-table').get(0));   "
                + "    setCollapsibleHeaders('#results .collapsible-header');   "
                + "    if (data.hasOwnProperty('charts')) {                    "
                + "      try {    "
                + "         var charts = JSON.parse(data.charts);                 "
                + "         for(var i = 0; i<charts.length; i++) {  "
                + "             var chart = Highcharts.chart('chart-'+i.toString(), charts[i]);"
                + "             chart.reflow();             "
                + "         }                        "
                + "      } catch (err) {"
                + "         $('#results').html(\"<div style='color:red;'>JavaScript error occured: \" + err.message + '</div>');"
                + "      }            "
                + "    }          "
                + "  }        "
                + "});"
                + "return false; ";
    }

    static Tag tableFromPatentList(Item[] items, List<String> attributes, String tableId, String dataURL) {
        return span().withClass("collapse show").withId("data-table").with(
                a("Download to Excel").withClass("btn btn-secondary div-button").attr("style","margin-left: 25%; margin-right: 25%; margin-bottom: 20px;").attr("onclick", "downloadTable('#"+tableId+"');"),
                table().withId(tableId).attr("data-url",dataURL).withClass("table table-striped").attr("style","margin-left: 15px; margin-right: 15px; width: auto;").with(
                        thead().with(
                                tr().with(
                                        attributes.stream().map(attr -> th(humanAttributeFor(attr)).withClass("sortable").attr("data-field", attr.toLowerCase())).collect(Collectors.toList())
                                )
                        ), tbody().with(
                                Arrays.stream(items).map(item -> {
                                    List<org.deeplearning4j.berkeley.Pair<String, Object>> results = item.getDataAsRow(attributes);
                                    return addAttributesToRow(tr().with(
                                            results.stream().map(pair -> createItemCell(pair.getSecond())).collect(Collectors.toList())
                                    ), results);
                                }).collect(Collectors.toList())
                        )
                )
        );
    }

    public static Tag addAttributesToRow(ContainerTag tag, List<org.deeplearning4j.berkeley.Pair<String,Object>> data) {
        AtomicReference<ContainerTag> ref = new AtomicReference<>(tag);
        data.forEach(pair->{
            ref.set(ref.get().attr("data-"+pair.getFirst(),pair.getSecond().toString()));
        });
        return ref.get();
    }

    public static ContainerTag createItemCell(Object cell) {
        return cell==null?td(""): ((cell instanceof Double || cell instanceof Float) ? (((Number)cell).doubleValue()==(double) ((Number)cell).intValue() ? td(String.valueOf(((Number)cell).intValue())) : td(String.format("%.1f",cell))) : td(cell.toString()));
    }


    public static List<String> preProcess(String toSplit, String delim, String toReplace) {
        if(toSplit==null||toSplit.trim().length()==0) return new ArrayList<>();
        return Arrays.asList(toSplit.split(delim)).stream().filter(str->str!=null).map(str->toReplace!=null&&toReplace.length()>0?str.trim().replaceAll(toReplace,""):str.trim()).filter(str->str!=null&&!str.isEmpty()).collect(Collectors.toList());
    }

    static Tag templateWrapper(Response res, Tag form) {
        res.type("text/html");
        return html().with(
                head().with(
                        script().withSrc("https://ajax.googleapis.com/ajax/libs/jquery/2.0.3/jquery.min.js"),
                        script().withSrc("https://code.jquery.com/ui/1.12.1/jquery-ui.js"),
                        script().withSrc("http://code.highcharts.com/highcharts.js"),
                        script().withSrc("/js/customEvents.js"),
                        script().withSrc("/js/defaults.js"),
                        script().withSrc("https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.3/js/select2.min.js"),
                        script().withSrc("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/js/bootstrap.min.js"),
                        script().withSrc("https://cdnjs.cloudflare.com/ajax/libs/tether/1.4.0/js/tether.min.js"),
                        link().withRel("stylesheet").withHref("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/css/bootstrap.min.css"),
                        link().withRel("stylesheet").withHref("https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.3/css/select2.min.css"),
                        link().withRel("stylesheet").withHref("/css/defaults.css"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().with(
                        div().withClass("container-fluid text-center").attr("style","height: 100%;").with(
                                div().withClass("row").attr("style","height: 100%;").with(
                                        nav().withClass("col-3 sidebar").attr("style","height: 100%; position: fixed; padding: 0px; padding-top: 15px;").with(
                                                a().attr("href", "/").with(
                                                        img().attr("style","display: block; margin-left: auto; margin-right: auto;")
                                                                .attr("src", "/images/brand.png")
                                                ),hr(),br(),
                                                h4("Templates"),br(),
                                                ul().withClass("nav nav-pills flex-column").with(
                                                    templates.stream().map(template->{
                                                        return li().withClass("nav-item").with(
                                                                a(template.getName()).withClass("btn btn-secondary").attr("style","width: 80%;").withHref(template.getHref())
                                                        );
                                                    }).collect(Collectors.toList())
                                                )
                                        ),div().withClass("col-9 offset-3").attr("style","padding-top: 58px; padding-left:0px; padding-right:0px;").with(
                                                customFormHeader(),
                                                form,
                                                br(),
                                                br(),
                                                br()
                                        )
                                )
                        )
                )
        );
    }


    public static Tag gatherTechnologySelect(String name) {
        return technologySelect(name,getTechTagger().getClassifications().stream().sorted().collect(Collectors.toList()));
    }


    public static Tag technologySelect(String name, List<String> orderedClassifications) {
        return select().attr("style","width:100%;").withName(name).withClass("multiselect").attr("multiple","multiple").with(
                orderedClassifications.stream().map(technology-> {
                    return div().with(option(humanAttributeFor(technology)).withValue(technology));
                }).collect(Collectors.toList())
        );
    }

    private static Tag candidateSetModelsForm() {
        return div().withClass("row").attr("style","margin-left: 0px; margin-right: 0px;").with(
                span().withId("main-content-id").withClass("collapse show").with(
                        form().withAction(DOWNLOAD_URL).withMethod("post").attr("style","margin-bottom: 0px;").withId(GENERATE_REPORTS_FORM_ID).with(
                                div().withClass("col-12").with(
                                        div().withClass("row").with(
                                                div().withClass("col-6 form-left form-top").with(
                                                        mainOptionsRow()
                                                ),div().withClass("col-6 form-right form-top").with(
                                                        customFormRow("charts",chartModelMap,CHART_MODELS_ARRAY_FIELD)
                                                )
                                        ), div().withClass("row").with(
                                                div().withClass("col-6 form-left form-bottom").with(
                                                        customFormRow("attributes", Arrays.asList(similarityEngine.getEngineMap(),attributesMap), Arrays.asList(SIMILARITY_ENGINES_ARRAY_FIELD,ATTRIBUTES_ARRAY_FIELD))
                                                ),div().withClass("col-6 form-right form-bottom").with(
                                                        customFormRow("filters", Arrays.asList(similarityFilterModelMap, preFilterModelMap, postFilterModelMap), Arrays.asList(SIMILARITY_FILTER_ARRAY_FIELD,PRE_FILTER_ARRAY_FIELD,POST_FILTER_ARRAY_FIELD))
                                                )
                                        )
                                ),div().withClass("col-12").with(
                                        div().withText("Generate Report").withClass("btn btn-secondary div-button").withId(GENERATE_REPORTS_FORM_ID+"-button")
                                                .attr("onclick", ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID, REPORT_URL,"Generate Report","Generating Report..."))
                                )
                        )
                ),
                div().withClass("col-12").withId("results").attr("style","padding-top: 20px;")
        );
    }


    private static Tag customFormHeader() {
        return div().withClass("row header-main header-top").with(
                div().withClass("col-2").with(
                        form().attr("style","display: flex;").attr("onsubmit",ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID+"-back", REPORT_URL,"Back","Going back"))
                                .withId(GENERATE_REPORTS_FORM_ID+"-back").with(
                                        input().withName("goBack").withValue("on").withType("hidden"), br(),
                                        button("Back").withClass("btn btn-sm btn-secondary").attr("style","margin-right: auto; width: 90%;").withId(GENERATE_REPORTS_FORM_ID+"-back"+"-button").withType("submit")
                        )
                ), div().withClass("col-8").with(
                        h4("Artificial Intelligence Platform").withClass("collapsible-header")
                                .attr("data-target","#main-content-id")
                ), div().withClass("col-2").with(
                        form().attr("style","display: flex;").attr("onsubmit",ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID+"-forward", REPORT_URL,"Forward","Going forward"))
                                .withId(GENERATE_REPORTS_FORM_ID+"-forward").with(
                                input().withName("goForward").withValue("on").withType("hidden"), br(),
                                button("Forward").withClass("btn btn-sm btn-secondary").attr("style","margin-left: auto; width: 90%;").withId(GENERATE_REPORTS_FORM_ID+"-forward"+"-button").withType("submit")
                        )
                )
        );
    }

    private static Tag toggleButton(String id, String text) {
        return div().withClass("row").with(
                div().withId(id+"-panel-toggle").withClass("col-12").with(
                        h5(text).withClass("collapsible-header").attr("data-target","#"+id)
                )
        );
    }
    private static Tag customFormRow(String type, Map<String, ? extends AbstractAttribute> modelMap, String arrayFieldName) {
        return customFormRow(type,Arrays.asList(modelMap),Arrays.asList(arrayFieldName));
    }

    private static Tag customFormRow(String type, List<Map<String, ? extends AbstractAttribute>> modelMaps, List<String> arrayFieldNames) {
        String shortTitle = type.substring(0,1).toUpperCase()+type.substring(1);
        List<Pair<Map<String,? extends AbstractAttribute>,String>> modelFields = new ArrayList<>();
        for(int i = 0; i < Math.min(modelMaps.size(),arrayFieldNames.size()); i++) {
            modelFields.add(new Pair<>(modelMaps.get(i),arrayFieldNames.get(i)));
        }
        String groupID = type+"-row";
        String toggleID = groupID+"-panel-toggle";
        return span().with(
                toggleButton(groupID, shortTitle),
                span().withId(groupID).withClass("collapse").with(
                        div().withClass("collapsible-form row").with(
                                div().withClass("col-12").with(
                                        select().withClass("display-item-select form-control").with(option("Search Available "+shortTitle+"...").withClass("placeholder").attr("selected","selected")),
                                        div().withClass("hidden-placeholder").attr("style","display: none;"),
                                        div().withClass("value").attr("style","display: none;")
                                ), div().attr("style","display: none;").withId(type+"-start").withClass("droppable start"+type).with(
                                        div().with(
                                                modelFields.stream().flatMap(pair->{
                                                    String arrayFieldName = pair._2;
                                                    return pair._1.entrySet().stream().map(e->{
                                                        String collapseId = "collapse-"+type+"-"+e.getKey().replaceAll("[\\[\\]]","");
                                                        return div().withClass("draggable "+type).attr("data-target",type).with(
                                                                div().attr("style","width: 100%;").withClass("collapsible-header").attr("data-target","#"+collapseId).with(
                                                                        label(humanAttributeFor(e.getKey())),
                                                                        input().attr("group-id",groupID).attr("toggle-id",toggleID).attr("disabled","disabled").withType("checkbox").withClass("mycheckbox").withName(arrayFieldName).withValue(e.getKey()),
                                                                        span().withClass("remove-button").withText("x")
                                                                ), span().withClass("collapse").withId(collapseId).with(e.getValue().getOptionsTag())
                                                        );
                                                    });
                                                }).collect(Collectors.toList())
                                        )
                                ), div().withId(type+"-target").withClass("droppable target col-12 "+type)
                        )
                )
        );
    }

    private static Tag mainOptionsRow() {
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        h5("Search Options").withClass("collapsible-header").attr("data-target","#main-options")
                ),
                span().withId("main-options").withClass("collapse").with(
                        div().withClass("col-12").with(
                                div().withClass("row collapsible-form").with(
                                        div().withClass("col-4").with(
                                                label("Result Type"),br(),
                                                select().withClass("form-control single-select2").withName(SEARCH_TYPE_FIELD).with(
                                                        Arrays.stream(PortfolioList.Type.values()).map(type->{
                                                            ContainerTag option = option(type.toString().substring(0,1).toUpperCase()+type.toString().substring(1)).withValue(type.toString());
                                                            if(type.equals(PortfolioList.Type.patents)) option=option.attr("selected","selected");
                                                            return option;
                                                        }).collect(Collectors.toList())
                                                )
                                        ),
                                        div().withClass("col-4").with(
                                                label("Maximization"),br(),select().withClass("form-control single-select2").withName(COMPARATOR_FIELD).with(
                                                        option("Similarity").attr("selected","selected").withValue(Constants.SIMILARITY),
                                                        option("AI Value").withValue(Constants.AI_VALUE)
                                                )
                                        ),
                                        div().withClass("col-4").with(
                                                label("Result Limit"),br(),input().withClass("form-control").attr("style","height: 28px;").withType("number").withValue("10").withName(LIMIT_FIELD)
                                        ), div().withClass("col-12").attr("style","margin-top: 8px;").with(
                                                label("Similarity Model"),br(),select().withClass("form-control single-select2").withName(SIMILARITY_MODEL_FIELD).with(
                                                        option().withValue(Constants.PARAGRAPH_VECTOR_MODEL).attr("selected","true").withText("Claim Language Model"),
                                                        option().withValue(Constants.SIM_RANK_MODEL).withText("Citation Graph Model"),
                                                        option().withValue(Constants.WIPO_MODEL).withText("WIPO Technology Model"),
                                                        option().withValue(Constants.CPC_MODEL).withText("CPC Code Model")
                                                )
                                        )
                                )
                        )
                )
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
        long t1 = System.currentTimeMillis();
        //Database.setupSeedConn();
        boolean preLoad = true;
        Database.initializeDatabase();
        System.out.println("Starting to load base finder...");
        initialize();
        System.out.println("Finished loading base finder.");
        System.out.println("Starting server...");
        server();
        System.out.println("Finished starting server.");
        if(preLoad)Database.preLoad();
        long t2 = System.currentTimeMillis();
        System.out.println("Time to start server: "+ ((t2-t1)/(1000*60)) + " minutes");
    }
}
