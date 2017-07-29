package user_interface.server;

import com.google.gson.Gson;
import elasticsearch.DataIngester;
import lombok.Getter;
import org.nd4j.linalg.api.ndarray.INDArray;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import j2html.tags.ContainerTag;
import user_interface.server.tools.AjaxChartMessage;
import user_interface.server.tools.BackButtonHandler;
import user_interface.ui_models.charts.*;
import user_interface.ui_models.engines.*;
import user_interface.ui_models.excel.ExcelHandler;
import user_interface.templates.*;
import util.Pair;
import models.similarity_models.AbstractSimilarityModel;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import models.dl4j_neural_nets.tools.MyPreprocessor;
import j2html.tags.Tag;

import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import seeding.Constants;
import seeding.Database;
import models.classification_models.ClassificationAttr;
import models.classification_models.TechTaggerNormalizer;
import user_interface.ui_models.filters.*;
import user_interface.ui_models.portfolios.items.Item;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import user_interface.ui_models.portfolios.PortfolioList;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;
import models.value_models.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static ClassificationAttr tagger;
    public static final String EXCEL_SESSION = "excel_data";
    public static final String PATENTS_TO_SEARCH_IN_FIELD = "patentsToSearchIn";
    public static final String ASSIGNEES_TO_SEARCH_IN_FIELD = "assigneesToSearchIn";
    public static final String PATENTS_TO_SEARCH_FOR_FIELD = "patentsToSearchFor";
    public static final String ASSIGNEES_TO_SEARCH_FOR_FIELD = "assigneesToSearchFor";
    public static final String TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD = "technologiesToSearchFor[]";
    public static final String CPC_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD = "cpcTechnologiesToFilter[]";
    public static final String TECHNOLOGIES_TO_FILTER_ARRAY_FIELD = "technologiesToFilter[]";
    public static final String SIMILARITY_ENGINES_ARRAY_FIELD = "similarityEngines[]";
    public static final String PRE_FILTER_ARRAY_FIELD = "preFilters[]";
    public static final String SIMILARITY_FILTER_ARRAY_FIELD = "similarityFilters[]";
    public static final String ATTRIBUTES_ARRAY_FIELD = "attributes[]";
    public static final String DO_NOTHING_FILTER_ARRAY_FIELD = "doNothingFilters[]";
    public static final String LIMIT_FIELD = "limit";
    public static final String SIMILARITY_MODEL_FIELD = "similarityModel";
    public static final String COMPARATOR_FIELD = "comparator";
    public static final String SORT_DIRECTION_FIELD = "sortDirection";
    public static final String CHARTS_GROUPED_BY_FIELD = "chartsGroupedBy";
    public static final String SEARCH_TYPE_ARRAY_FIELD = "searchType[]";
    public static final String CHART_MODELS_ARRAY_FIELD = "chartModels[]";
    public static final String REPORT_URL = "/patent_recommendation_engine";
    public static final String DOWNLOAD_URL = "/excel_generation";
    public static final String WIPO_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD = "wipoTechnologiesToFilter[]";
    private static AbstractSimilarityModel DEFAULT_SIMILARITY_MODEL;
    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    public static Map<String,AbstractSimilarityModel> similarityModelMap = new HashMap<>();
    public static SimilarityEngineController similarityEngine;
    public static Map<String,AbstractFilter> preFilterModelMap = new HashMap<>();
    public static Map<String,AbstractFilter> doNothingFilterModelMap = new HashMap<>();
    public static Map<String,AbstractFilter> similarityFilterModelMap = new HashMap<>();
    static Map<String,AbstractAttribute> attributesMap = new HashMap<>();
    static Map<String,ChartAttribute> chartModelMap = new HashMap<>();
    static List<FormTemplate> templates = new ArrayList<>();

    static Collection<? extends AbstractAttribute> preComputedAttributes;
    @Getter
    static Collection<String> allAttributeNames;

    protected static Map<String,String> humanAttrToJavaAttrMap;
    protected static Map<String,String> javaAttrToHumanAttrMap;
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        { // Attributes
            humanAttrToJavaAttrMap = new HashMap<>();
            humanAttrToJavaAttrMap.put("Asset Number", Constants.NAME);
            humanAttrToJavaAttrMap.put("Similarity", Constants.SIMILARITY);
            humanAttrToJavaAttrMap.put("Technology Similarity", Constants.TECHNOLOGY_SIMILARITY);
            humanAttrToJavaAttrMap.put("Assignee Similarity", Constants.ASSIGNEE_SIMILARITY);
            humanAttrToJavaAttrMap.put("Asset Similarity", Constants.PATENT_SIMILARITY);
            humanAttrToJavaAttrMap.put("Total Asset Count", Constants.TOTAL_ASSET_COUNT);
            humanAttrToJavaAttrMap.put("Assignee", Constants.ASSIGNEE);
            humanAttrToJavaAttrMap.put("Invention Title", Constants.INVENTION_TITLE);
            humanAttrToJavaAttrMap.put("AI Value", Constants.AI_VALUE);
            humanAttrToJavaAttrMap.put("Result Type", Constants.RESULT_TYPE_FILTER);
            humanAttrToJavaAttrMap.put("Is Expired", Constants.EXPIRED);
            humanAttrToJavaAttrMap.put("Japanese Assignee", Constants.JAPANESE_ASSIGNEE);
            humanAttrToJavaAttrMap.put("GTT Group Technology", Constants.TECHNOLOGY);
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
            humanAttrToJavaAttrMap.put("Remove Assets", Constants.LABEL_FILTER);
            humanAttrToJavaAttrMap.put("Portfolio Size", Constants.PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Patents",PortfolioList.Type.patents.toString());
            humanAttrToJavaAttrMap.put("Assignees",PortfolioList.Type.assignees.toString());
            humanAttrToJavaAttrMap.put("Applications",PortfolioList.Type.applications.toString());
            humanAttrToJavaAttrMap.put("Pie Chart", Constants.PIE_CHART);
            humanAttrToJavaAttrMap.put("Histogram",Constants.HISTOGRAM);
            humanAttrToJavaAttrMap.put("Patent Scope", Constants.PATENT_SEARCH_SCOPE_FILTER);
            humanAttrToJavaAttrMap.put("Assignee Scope", Constants.ASSIGNEE_SEARCH_SCOPE_FILTER);
            humanAttrToJavaAttrMap.put("WIPO Technology",Constants.WIPO_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Remaining Life (Years)",Constants.REMAINING_LIFE);
            humanAttrToJavaAttrMap.put("Minimum Remaining Life (Years)",Constants.REMAINING_LIFE_FILTER);
            humanAttrToJavaAttrMap.put("Require Keywords", Constants.REQUIRE_KEYWORD_FILTER);
            humanAttrToJavaAttrMap.put("Related Documents", Constants.PATENT_FAMILY);
            humanAttrToJavaAttrMap.put("Exclude Keywords", Constants.EXCLUDE_KEYWORD_FILTER);
            humanAttrToJavaAttrMap.put("Advanced Keyword Filter", Constants.ADVANCED_KEYWORD_FILTER);
            humanAttrToJavaAttrMap.put("Expiration Date", Constants.EXPIRATION_DATE);
            humanAttrToJavaAttrMap.put("Term Adjustments (Days)", Constants.PATENT_TERM_ADJUSTMENT);
            humanAttrToJavaAttrMap.put("CPC Technology", Constants.CPC_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Overall Score", Constants.OVERALL_SCORE);

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

    public static void initialize(boolean loadData) {
        loadAttributes(loadData);
        loadFilterModels();
        loadTechTaggerModel();
        loadChartModels();
        loadTemplates();
        //loadAllItemsWithAttributes();
        loadSimilarityModels();
    }

    public static void loadChartModels() {
        chartModelMap.put(Constants.PIE_CHART, new AbstractDistributionChart());
        chartModelMap.put(Constants.HISTOGRAM, new AbstractHistogramChart());
    }


    public static void loadTemplates() {
        if(templates.isEmpty()) {
            templates.add(new PortfolioAssessment());
            templates.add(new SimilarAssetSearch());
            templates.add(new SimilarAssigneeSearch());
            templates.add(new FormTemplate("Reset Form",new HashMap<>(), FormTemplate.defaultOptions(),Collections.emptyList()));
        }
    }

    public static void loadFilterModels() {
        if(preFilterModelMap.isEmpty()&&doNothingFilterModelMap.isEmpty()&&similarityFilterModelMap.isEmpty()) {
            try {
                // Do nothing filters
                    // None exist at the moment...
                    // Use doNothingFilterModelMap

                // Pre filters
                preFilterModelMap.put(Constants.RESULT_TYPE_FILTER, new ResultTypeFilter());
                preFilterModelMap.put(Constants.REQUIRE_KEYWORD_FILTER, new RequireKeywordFilter());
                preFilterModelMap.put(Constants.EXCLUDE_KEYWORD_FILTER, new ExcludeKeywordFilter());
                preFilterModelMap.put(Constants.ADVANCED_KEYWORD_FILTER, new AdvancedKeywordFilter());
                preFilterModelMap.put(Constants.PATENT_SEARCH_SCOPE_FILTER, new IncludeLabelFilter());
                preFilterModelMap.put(Constants.ASSIGNEE_SEARCH_SCOPE_FILTER, new IncludeAssigneeFilter());
                preFilterModelMap.put(Constants.LABEL_FILTER,new RemoveLabelFilter());
                preFilterModelMap.put(Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER,new PortfolioSizeMaximumFilter());
                preFilterModelMap.put(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER,new PortfolioSizeMinimumFilter());
                preFilterModelMap.put(Constants.ASSIGNEES_TO_REMOVE_FILTER, new RemoveAssigneeFilter());
                preFilterModelMap.put(Constants.NO_JAPANESE_FILTER, new RemoveJapaneseAssigneeFilter());
                preFilterModelMap.put(Constants.JAPANESE_ONLY_FILTER, new IncludeJapaneseAssigneeFilter());
                preFilterModelMap.put(Constants.VALUE_THRESHOLD_FILTER,new ValueThresholdFilter());
                preFilterModelMap.put(Constants.EXPIRATION_FILTER,new ExpirationFilter());
                preFilterModelMap.put(Constants.COMPDB_ASSETS_PURCHASED, new CompDBAssetsPurchasedFilter());
                preFilterModelMap.put(Constants.COMPDB_ASSETS_SOLD, new CompDBAssetsSoldFilter());
                preFilterModelMap.put(Constants.WIPO_TECHNOLOGY, new WIPOTechnologyFilter());
                preFilterModelMap.put(Constants.CPC_TECHNOLOGY, new CPCTechnologyFilter());
                preFilterModelMap.put(Constants.REMAINING_LIFE_FILTER, new RemainingLifeFilter());
                preFilterModelMap.put(Constants.TECHNOLOGY,new TechnologyFilter());

                // During filters
                similarityFilterModelMap.put(Constants.SIMILARITY_THRESHOLD_FILTER,new SimilarityThresholdFilter());

                // pre computed attributes
                preComputedAttributes = getAttributesFromPrerequisites(preFilterModelMap.values(), new HashSet<>());
                allAttributeNames = preComputedAttributes.stream().map(attr->attr.getName()).collect(Collectors.toList());
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadSimilarityModels() {
        if(similarityModelMap.isEmpty()) {
            if(DEFAULT_SIMILARITY_MODEL==null) DEFAULT_SIMILARITY_MODEL = new SimilarPatentFinder(Collections.emptyList());
            similarityModelMap.put(Constants.PARAGRAPH_VECTOR_MODEL, DEFAULT_SIMILARITY_MODEL);
        }
    }

    public static void loadAttributes(boolean loadData) {
        if(attributesMap.isEmpty()) {
            attributesMap.put(Constants.JAPANESE_ASSIGNEE, new JapaneseAttribute());
            attributesMap.put(Constants.EXPIRED, new ExpiredAttribute());
            attributesMap.put(Constants.INVENTION_TITLE, new InventionTitleAttribute());
            attributesMap.put(Constants.ASSIGNEE, new AssigneeNameAttribute());
            attributesMap.put(Constants.PORTFOLIO_SIZE, new PortfolioSizeAttribute());
            attributesMap.put(Constants.TECHNOLOGY, new TechnologyAttribute());
            attributesMap.put(Constants.NAME, new NameAttribute());
            attributesMap.put(Constants.WIPO_TECHNOLOGY, new WIPOTechnologyAttribute());
            attributesMap.put(Constants.COMPDB_ASSETS_PURCHASED, new CompDBAssetsPurchasedAttribute());
            attributesMap.put(Constants.COMPDB_ASSETS_SOLD, new CompDBAssetsSoldAttribute());
            attributesMap.put(Constants.AI_VALUE, new OverallEvaluator(loadData));
            attributesMap.put(Constants.REMAINING_LIFE, new RemainingLifeAttribute());
            attributesMap.put(Constants.PATENT_FAMILY, new FamilyMembersAttribute());
            attributesMap.put(Constants.EXPIRATION_DATE, new ExpirationDateAttribute());
            attributesMap.put(Constants.PATENT_TERM_ADJUSTMENT, new PatentTermAdjustmentAttribute());
            attributesMap.put(Constants.CPC_TECHNOLOGY, new CPCTechnologyAttribute());

            if(DEFAULT_SIMILARITY_MODEL==null) DEFAULT_SIMILARITY_MODEL = new SimilarPatentFinder(Collections.emptyList());
            // similarity engine
            similarityEngine = new SimilarityEngineController(Arrays.asList(new PatentSimilarityEngine(DEFAULT_SIMILARITY_MODEL), new AssigneeSimilarityEngine(DEFAULT_SIMILARITY_MODEL), new TechnologySimilarityEngine(DEFAULT_SIMILARITY_MODEL)));
        }
    }

    public static void loadAndIngestAllItemsWithAttributes(Map<String,INDArray> lookupTable, int batchSize, Collection<String> onlyAttributes) {
        handleItemsList(new ArrayList<>(Database.getCopyOfAllApplications()), lookupTable, batchSize, PortfolioList.Type.applications, onlyAttributes);
        handleItemsList(new ArrayList<>(Database.getCopyOfAllPatents()), lookupTable, batchSize, PortfolioList.Type.patents, onlyAttributes);
        handleItemsList(new ArrayList<>(Database.getAssignees()), lookupTable, batchSize, PortfolioList.Type.assignees, onlyAttributes);
    }

    private static void handleItemsList(List<String> inputs, Map<String,INDArray> lookupTable, int batchSize, PortfolioList.Type type, Collection<String> onlyAttributes) {
        AtomicInteger cnt = new AtomicInteger(0);
        Collection<? extends AbstractAttribute> attributes = preComputedAttributes.stream().filter(attr->onlyAttributes.contains(attr.getName())).collect(Collectors.toList());
        chunked(inputs,batchSize).parallelStream().forEach(batch -> {
            Collection<Item> items = batch.stream().map(label->{
                Item item = new Item(label);
                INDArray vector = lookupTable.get(label);
                if(vector==null) return null;
                float[] data = vector.divi(vector.norm2Number().doubleValue()).data().asFloat();
                Map<String,Float> obj = new HashMap<>();
                for(int i = 0; i < data.length; i++) {
                    obj.put(String.valueOf(i),data[i]);
                }
                //item.addData("vector", data);
                item.addData("vector_obj",obj);
                attributes.forEach(model -> {
                    item.addData(model.getName(), model.attributesFor(Arrays.asList(item.getName()), 1));
                });
                return item;
            }).filter(item->item!=null).collect(Collectors.toList());

            DataIngester.ingestItems(items, type);
            cnt.getAndAdd(items.size());
            System.out.println("Seen "+cnt.get()+" "+type.toString());
        });
    }

    private static List<Collection<String>> chunked(List<String> items, int batchSize) {
        List<Collection<String>> chunks = new ArrayList<>((items.size()+1)/batchSize);
        for(int i = 0; i < items.size(); i+= batchSize) {
            List<String> chunk = new ArrayList<>(batchSize);
            for(int j = i; j < (Math.min(i+ batchSize, items.size())); j++) {
                chunk.add(items.get(j));
            }
            if(chunk.size() > 0) chunks.add(chunk);
        }
        return chunks;
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
        return dependentAttributes.stream().flatMap(dependency ->(Stream<String>)dependency.getPrerequisites().stream().filter(r->r!=null)).distinct().filter(preReq -> !appliedAttributes.contains(preReq)).map(preReq -> {
            AbstractAttribute attr = attributesMap.get(preReq);
            if (attr != null) {
                appliedAttributes.add(preReq);
            }
            return attr;
        }).filter(model -> model != null).collect(Collectors.toList());
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
            System.out.println("Received excel request");
            long t0 = System.currentTimeMillis();
            HttpServletResponse raw = res.raw();
            Map<String,Object> map = req.session(false).attribute(EXCEL_SESSION);
            if(map==null) return null;
            List<String> headers = (List<String>)map.getOrDefault("headers",Collections.emptyList());
            System.out.println("Number of excel headers: "+headers.size());
            List<List<String>> data = (List<List<String>>)map.getOrDefault("rows",Collections.emptyList());
            res.header("Content-Disposition", "attachment; filename=download.xls");
            res.type("application/force-download");
            ExcelHandler.writeDefaultSpreadSheetToRaw(raw, "Data", "Data", data,  headers);
            long t1 = System.currentTimeMillis();
            System.out.println("Time to create excel sheet: "+(t1-t0)/1000+ " seconds");
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
            // Sorted by
            String comparator = extractString(req,COMPARATOR_FIELD,Constants.OVERALL_SCORE);
            // Get Models to use
            List<String> similarityFilterModels = extractArray(req, SIMILARITY_FILTER_ARRAY_FIELD);
            List<String> itemAttributes = extractArray(req, ATTRIBUTES_ARRAY_FIELD);
            List<String> chartModels = extractArray(req, CHART_MODELS_ARRAY_FIELD);
            List<String> similarityEngines = extractArray(req, SIMILARITY_ENGINES_ARRAY_FIELD);

            System.out.println(" ... Attributes");
            // Get data attributes
            List<AbstractAttribute> attributes = itemAttributes.stream().map(attr -> attributesMap.get(attr)).collect(Collectors.toList());

            System.out.println(" ... Filters");
            // Get filters
            List<AbstractFilter> similarityFilters = similarityFilterModels.stream().map(modelName -> similarityFilterModelMap.get(modelName)).collect(Collectors.toList());

            // Update filters based on params
            similarityFilters.stream().forEach(filter -> filter.extractRelevantInformationFromParams(req));

            System.out.println(" ... Evaluators");
            // Get value models

            Set<String> appliedAttributes = new HashSet<>(preComputedAttributes.stream().map(model->model.getName()).collect(Collectors.toList()));
            // get portfolio list from similarity engine
            similarityEngine.extractRelevantInformationFromParams(req);
            PortfolioList portfolioList = similarityEngine.getPortfolioList();

            // Apply attributes
            System.out.println("Applying attributes...");
            portfolioList.applyAttributes(attributes.stream().filter(attr->!appliedAttributes.contains(attr.getName())).collect(Collectors.toList()));
            appliedAttributes.addAll(itemAttributes);

            List<String> tableHeaders = new ArrayList<>(itemAttributes);
            if(similarityEngines.size()>0) {
                tableHeaders.add(0,Constants.SIMILARITY);
            }
            if(comparator.equals(Constants.OVERALL_SCORE)) {
                tableHeaders.add(0,Constants.OVERALL_SCORE);
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
            List<List<String>> tableData = getTableRowData(portfolioList.getItemList(), tableHeaders);
            AtomicInteger chartCnt = new AtomicInteger(0);
            String html = new Gson().toJson(new AjaxChartMessage(div().with(
                    finishedCharts.isEmpty() ? div() : div().withClass("row").attr("style","margin-bottom: 10px;").with(
                            h4("Charts").withClass("collapsible-header").attr("data-target","#data-charts"),
                            span().withId("data-charts").withClass("collapse show").with(
                                    finishedCharts.stream().map(c -> div().attr("style","width: 80%; margin-left: 10%; margin-bottom: 30px;").withClass(c.getType()).withId("chart-" + chartCnt.getAndIncrement())).collect(Collectors.toList())
                            ),br()
                    ),portfolioList == null ? div() : div().withClass("row").attr("style","margin-top: 10px;").with(
                            h4("Data").withClass("collapsible-header").attr("data-target","#data-table"),
                            tableFromPatentList(tableData, tableHeaders)
                    )
            ).render(), finishedCharts));

            navigator.addRequest(html);
            Map<String,Object> excelRequestMap = new HashMap<>();
            excelRequestMap.put("headers", tableHeaders);
            excelRequestMap.put("rows", tableData);
            req.session().attribute(EXCEL_SESSION, excelRequestMap);

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
                // No longer using custom function
                //+ "             if( $('#'+'chart-'+i.toString()).hasClass('column') ) { "
                //+ "                 charts[i].chart.events = { render: loadEvent }; "
                //+ "             }   "
                + "             var chart = Highcharts.chart('chart-'+i.toString(), charts[i]);"
                + "             chart.redraw();             "
                + "         }                        "
                + "      } catch (err) {"
                + "         $('#results').html(\"<div style='color:red;'>JavaScript error occured: \" + err.message + '</div>');"
                + "      }            "
                + "    }          "
                + "  }        "
                + "});"
                + "return false; ";
    }

    static Tag tableFromPatentList(List<List<String>> data, List<String> attributes) {
        return span().withClass("collapse show").withId("data-table").with(
                form().withMethod("post").withTarget("_blank").withAction(DOWNLOAD_URL).with(
                        button("Download to Excel").withType("submit").withClass("btn btn-secondary div-button").attr("style","margin-left: 25%; margin-right: 25%; margin-bottom: 20px;")
                ), table().withClass("table table-striped").attr("style","margin-left: 3%; margin-right: 3%; width: 94%;").with(
                        thead().with(
                                tr().with(
                                        attributes.stream().map(attr -> th(humanAttributeFor(attr)).withClass("sortable").attr("data-field", attr.toLowerCase())).collect(Collectors.toList())
                                )
                        ), tbody().with(
                                data.stream().map(results -> {
                                    return addAttributesToRow(tr().with(
                                            results.stream().map(value -> td(value)).collect(Collectors.toList())
                                    ), results, attributes);
                                }).collect(Collectors.toList())
                        )
                )
        );
    }

    static List<List<String>> getTableRowData(Item[] items, List<String> attributes) {
        return Arrays.stream(items).map(item -> item.getDataAsRow(attributes)).collect(Collectors.toList());
    }

    public static Tag addAttributesToRow(ContainerTag tag, List<String> data, List<String> headers) {
        AtomicReference<ContainerTag> ref = new AtomicReference<>(tag);
        for(int i = 0; i < data.size(); i++) {
            ref.set(ref.get().attr("data-"+headers.get(i),data.get(i).toString()));
        }
        return ref.get();
    }

    public static List<String> preProcess(String toSplit, String delim, String toReplace) {
        if(toSplit==null||toSplit.trim().length()==0) return new ArrayList<>();
        return Arrays.asList(toSplit.split(delim)).stream().filter(str->str!=null).map(str->toReplace!=null&&toReplace.length()>0?str.trim().replaceAll(toReplace,""):str.trim()).filter(str->str!=null&&!str.isEmpty()).collect(Collectors.toList());
    }

    static Tag templateWrapper(Response res, Tag form) {
        res.type("text/html");
        return html().with(
                head().with(
                        script().withSrc("https://code.jquery.com/jquery-3.1.0.js"),
                        script().withSrc("https://code.jquery.com/ui/1.12.1/jquery-ui.js"),
                        script().withSrc("http://code.highcharts.com/highcharts.js"),
                        script().withSrc("http://code.highcharts.com/modules/exporting.js"),
                        script().withSrc("http://code.highcharts.com/modules/offline-exporting.js"),
                        script().withSrc("/js/customEvents.js"),
                        script().withSrc("/js/defaults.js"),
                        script().withSrc("/js/jquery.miniTip.js"),
                        script().withSrc("https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.3/js/select2.min.js"),
                        script().withSrc("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/js/bootstrap.min.js"),
                        script().withSrc("https://cdnjs.cloudflare.com/ajax/libs/tether/1.4.0/js/tether.min.js"),
                        link().withRel("stylesheet").withHref("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/css/bootstrap.min.css"),
                        link().withRel("stylesheet").withHref("https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.3/css/select2.min.css"),
                        link().withRel("stylesheet").withHref("/css/defaults.css"),
                        link().withRel("stylesheet").withHref("/css/miniTip.css"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().with(
                        div().withClass("container-fluid text-center").attr("style","height: 100%;").with(
                                div().withClass("row").attr("style","height: 100%;").with(
                                        nav().withClass("col-3 sidebar").attr("style","height: 100%; position: fixed; padding: 0px; padding-top: 15px;").with(
                                                br(),br(),br(),
                                                h4("Templates").attr("style","margin-top: 50px;"),br(),
                                                ul().withClass("nav nav-pills flex-column").with(
                                                    templates.stream().map(template->templateHelper(template,80)).collect(Collectors.toList())
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

    public static Tag templateHelper(FormTemplate template, int width) {
        if(template.nestedForms().isEmpty()) {
            return li().withClass("nav-item").with(
                    a(template.getName()).withClass("btn btn-secondary").attr("style","width: "+width+"%;").withHref(template.getHref())
            );
        } else {
            return li().withClass("nav-item").with(
                    button(template.getName()).withClass("btn btn-secondary").attr("style","width: "+width+"%;").attr("onclick","$(this).next().slideToggle();"),
                    ul().withClass("nav nav-pills flex-column").attr("style","display: none;").with(
                            template.nestedForms().stream().map(nested->{
                                return templateHelper(nested,width-5);
                            }).collect(Collectors.toList())
                    )
            );
        }
    }


    public static Tag gatherTechnologySelect(String name) {
        return technologySelect(name,getTechTagger().getClassifications().stream().sorted().collect(Collectors.toList()));
    }


    public static Tag technologySelect(String name, Collection<String> orderedClassifications) {
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
                                                        customFormRow("filters", Arrays.asList(similarityFilterModelMap, preFilterModelMap, doNothingFilterModelMap), Arrays.asList(SIMILARITY_FILTER_ARRAY_FIELD,PRE_FILTER_ARRAY_FIELD,DO_NOTHING_FILTER_ARRAY_FIELD))
                                                )
                                        )
                                ),div().with(
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
                div().withClass("col-3").attr("style","padding-top: 30px; border-right: 1px lightgray solid;").with(
                        a().attr("href", "/").with(
                                img().attr("style","display: block; margin-left: auto; margin-right: auto; margin-top: -20px")
                                        .attr("src", "/images/brand.png")
                        )
                ),div().withClass("col-9").attr("style","padding-top: 30px; background-color: #c4c4c4;").with(
                        div().withClass("row").with(
                                div().withClass("col-2").with(
                                        form().attr("style","display: flex;").attr("onsubmit",ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID+"-back", REPORT_URL,"Back","Back"))
                                                .withId(GENERATE_REPORTS_FORM_ID+"-back").with(
                                                input().withName("goBack").withValue("on").withType("hidden"), br(),
                                                button("Back").withClass("btn btn-sm btn-secondary").attr("style","margin-right: auto; width: 90%;").withId(GENERATE_REPORTS_FORM_ID+"-back"+"-button").withType("submit")
                                        )
                                ), div().withClass("col-8").with(
                                        h3("Artificial Intelligence Platform").withClass("collapsible-header")
                                                .attr("data-target","#main-content-id")
                                                .attr("style","margin-top: -5px;")
                                ), div().withClass("col-2").with(
                                        form().attr("style","display: flex;").attr("onsubmit",ajaxSubmitWithChartsScript(GENERATE_REPORTS_FORM_ID+"-forward", REPORT_URL,"Next","Next"))
                                                .withId(GENERATE_REPORTS_FORM_ID+"-forward").with(
                                                input().withName("goForward").withValue("on").withType("hidden"), br(),
                                                button("Next").withClass("btn btn-sm btn-secondary").attr("style","margin-left: auto; width: 90%;").withId(GENERATE_REPORTS_FORM_ID+"-forward"+"-button").withType("submit")
                                        )
                                )
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
                                        div().withClass("col-12").with(
                                                label("Sort By"),br(),select().withClass("form-control single-select2").withName(COMPARATOR_FIELD).with(
                                                        Arrays.asList(Constants.OVERALL_SCORE,Constants.SIMILARITY, Constants.AI_VALUE, Constants.PORTFOLIO_SIZE, Constants.REMAINING_LIFE, Constants.COMPDB_ASSETS_PURCHASED, Constants.COMPDB_ASSETS_SOLD).stream()
                                                                .map(key->option(humanAttributeFor(key)).withValue(key)).collect(Collectors.toList())
                                                )
                                        ),
                                        div().withClass("col-6").with(
                                                label("Sort Direction"),br(),
                                                select().withClass("form-control single-select2").withName(SORT_DIRECTION_FIELD).with(
                                                        option("Ascending").withValue("asc"),
                                                        option("Descending").withValue("desc").attr("selected","selected")
                                                )
                                        ),
                                        div().withClass("col-6").with(
                                                label("Result Limit"),br(),input().withClass("form-control").attr("style","height: 28px;").withType("number").withValue("10").withName(LIMIT_FIELD)
                                        ), div().withClass("col-12").attr("style","margin-top: 8px; display: none;").with(
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
        boolean initDatabase = false;

        if(initDatabase) Database.initializeDatabase();

        System.out.println("Starting to load base finder...");
        initialize(false);
        System.out.println("Finished loading base finder.");
        System.out.println("Starting user_interface.server...");
        server();
        System.out.println("Finished starting user_interface.server.");
        if(preLoad)Database.preLoad();
        long t2 = System.currentTimeMillis();
        System.out.println("Time to start user_interface.server: "+ ((t2-t1)/(1000*60)) + " minutes");
    }
}
