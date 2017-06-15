package server;

import j2html.tags.ContainerTag;
import ui_models.portfolios.attributes.AssigneeNameAttribute;
import ui_models.portfolios.attributes.InventionTitleAttribute;
import ui_models.portfolios.attributes.PortfolioSizeAttribute;
import util.Pair;
import similarity_models.AbstractSimilarityModel;
import similarity_models.cpc_vectors.CPCSimilarityFinder;
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
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    private static final String SELECT_BETWEEN_CANDIDATES_FORM_ID = "select-between-candidates-form";

    private static ClassificationAttr tagger;
    private static final String PATENTS_TO_SEARCH_IN_FIELD = "patentsToSearchIn";
    private static final String ASSIGNEES_TO_SEARCH_IN_FIELD = "assigneesToSearchIn";
    private static final String PATENTS_TO_SEARCH_FOR_FIELD = "patentsToSearchFor";
    private static final String ASSIGNEES_TO_SEARCH_FOR_FIELD = "assigneesToSearchFor";
    private static final String TECHNOLOGIES_TO_SEARCH_FOR_FIELD = "technologiesToSearchFor";
    private static final String VALUE_MODELS_ARRAY_FIELD = "valueModels[]";
    private static final String PRE_FILTER_ARRAY_FIELD = "preFilters[]";
    private static final String POST_FILTER_ARRAY_FIELD = "postFilters[]";
    private static final String ATTRIBUTES_ARRAY_FIELD = "attributes[]";
    private static final String SIMILARITY_MODEL_FIELD = "similarityModel";
    private static final String COMPARATOR_FIELD = "comparator";
    private static final String SEARCH_TYPE_FIELD = "searchType";

    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    static Map<String,ValueAttr> valueModelMap = new HashMap<>();
    static Map<String,AbstractSimilarityModel> similarityModelMap = new HashMap<>();
    static Map<String,AbstractFilter> preFilterModelMap = new HashMap<>();
    static Map<String,AbstractFilter> postFilterModelMap = new HashMap<>();
    static Map<String,AbstractAttribute> attributesMap = new HashMap<>();

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
            humanAttrToJavaAttrMap.put("Value Threshold",Constants.VALUE_THRESHOLD_FILTER);
            humanAttrToJavaAttrMap.put("Remove Non-Japanese Assignees Filter",Constants.JAPANESE_ONLY_FILTER);
            humanAttrToJavaAttrMap.put("Remove Japanese Assignees Filter",Constants.NO_JAPANESE_FILTER);
            humanAttrToJavaAttrMap.put("Remove Expired Assets Filter", Constants.EXPIRATION_FILTER);
            humanAttrToJavaAttrMap.put("Remove Assignees Filter", Constants.ASSIGNEES_TO_REMOVE_FILTER);
            humanAttrToJavaAttrMap.put("Remove Assets Filter", Constants.LABEL_FILTER);
            humanAttrToJavaAttrMap.put("Portfolio Size", Constants.PORTFOLIO_SIZE);

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
    }

    public static void loadValueModels() {
        if(valueModelMap.isEmpty()) {
            try {
                valueModelMap.put(Constants.AI_VALUE, new OverallEvaluator());
                valueModelMap.put(Constants.COMPDB_ASSETS_PURCHASED_VALUE, new CompDBAssetsPurchasedEvaluator());
                valueModelMap.put(Constants.COMPDB_ASSETS_SOLD_VALUE, new CompDBAssetsSoldEvaluator());
                valueModelMap.put(Constants.LARGE_PORTFOLIO_VALUE, new PortfolioSizeEvaluator());
                valueModelMap.put(Constants.SMALL_PORTFOLIO_VALUE, new SmallPortfolioSizeEvaluator());

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
                preFilterModelMap.put(Constants.LABEL_FILTER,new LabelFilter());
                // Post filters
                postFilterModelMap.put(Constants.VALUE_THRESHOLD_FILTER,new ValueThresholdFilter());
                postFilterModelMap.put(Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER,new PortfolioSizeMaximumFilter());
                postFilterModelMap.put(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER,new PortfolioSizeMinimumFilter());
                postFilterModelMap.put(Constants.EXPIRATION_FILTER,new ExpirationFilter());
                postFilterModelMap.put(Constants.ASSIGNEES_TO_REMOVE_FILTER, new AssigneeFilter());
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadSimilarityModels() {
        if(similarityModelMap.isEmpty()) {
            try {
                similarityModelMap.put(Constants.PARAGRAPH_VECTOR_MODEL+"_patents",new SimilarPatentFinder(Database.getValuablePatents(), "** Paragraph Vector Model **"));
                similarityModelMap.put(Constants.PARAGRAPH_VECTOR_MODEL+"_assignees", new SimilarPatentFinder(Database.getAssignees(), "** Paragraph Vector Model **"));
                similarityModelMap.put(Constants.SIM_RANK_MODEL+"_patents", new SimRankSimilarityModel(Database.getValuablePatents(),"** SimRank Model **"));
                similarityModelMap.put(Constants.CPC_MODEL+"_patents", new CPCSimilarityFinder(Database.getValuablePatents(), "** CPC Model **"));
                similarityModelMap.put(Constants.CPC_MODEL+"_assignees",new CPCSimilarityFinder(Database.getAssignees(), "** CPC Model **"));

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
        portfolioList.applyAttribute(model);
        System.out.println("Finished "+model.getName());
    }

    static String getAndRemoveMessage(Session session) {
        String message = session.attribute("message");
        if(message!=null)session.removeAttribute("message");
        return message;
    }

    private static Tag homePage() {
        return div().with(
                h3().with(a("Portfolio Comparison").withHref("/candidate_set_models")),h3().with(a("Company Profiler").withHref("/company_profile")),
                h3().with(a("Lead Development").withHref("/lead_development")),
                h3().with(a("Tech Tagger").withHref("/tech_tagger")),
                h3().with(a("Asset Valuation").withHref("/asset_valuation")),
                h3().with(a("Additional Patent Tools").withHref("/patent_toolbox"))
        );
    }

    public static void server() {
        port(4568);
        // GET METHODS
        get("/", (req, res) -> templateWrapper(res, div().with(homePage(),hr()), getAndRemoveMessage(req.session())));
        get("/candidate_set_models", (req, res) -> templateWrapper(res, div().with(candidateSetModelsForm(), hr()), getAndRemoveMessage(req.session())));


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

        post("/similar_candidate_sets", (req, res) -> {
            try {
                System.out.println("Getting parameters...");
                // get meta parameters
                int limit = extractInt(req, "limit", 10);
                String searchType = extractString(req, SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
                PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(searchType);
                String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY_COMPARATOR);

                System.out.println("Collecting inputs to search for...");
                // get input data
                // TODO Handle Gather Technology as input
                Collection<String> inputsToSearchFor;
                if(portfolioType.equals(PortfolioList.Type.patents)) {
                    inputsToSearchFor=new HashSet<>(preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]"));
                    new HashSet<>(preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]")).forEach(assignee->{
                       inputsToSearchFor.addAll(Database.selectPatentNumbersFromAssignee(assignee));
                    });
                } else {
                    inputsToSearchFor=new HashSet<>(preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));
                }

                System.out.println("Collecting inputs to search in...");
                // Get scope of search
                Collection<String> inputsToSearchIn;
                if(portfolioType.equals(PortfolioList.Type.patents)) {
                    inputsToSearchIn=new HashSet<>(preProcess(extractString(req, PATENTS_TO_SEARCH_IN_FIELD, ""), "\\s+", "[^0-9]"));
                    new HashSet<>(preProcess(extractString(req, ASSIGNEES_TO_SEARCH_IN_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]")).forEach(assignee->inputsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
                } else {
                    inputsToSearchIn=new HashSet<>(preProcess(extractString(req, ASSIGNEES_TO_SEARCH_IN_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));
                }

                // Check whether to search entire database
                boolean searchEntireDatabase = inputsToSearchIn.isEmpty();

                System.out.println("Getting models...");
                // Get Models to use
                String similarityModel = extractString(req,SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
                List<String> valueModels = extractArray(req,VALUE_MODELS_ARRAY_FIELD);
                List<String> preFilterModels = extractArray(req,PRE_FILTER_ARRAY_FIELD);
                List<String> postFilterModels = extractArray(req,POST_FILTER_ARRAY_FIELD);
                List<String> itemAttributes = extractArray(req,ATTRIBUTES_ARRAY_FIELD);
                List<String> technologies = extractArray(req,TECHNOLOGIES_TO_SEARCH_FOR_FIELD);

                // Get data attributes
                List<AbstractAttribute> attributes = itemAttributes.stream().map(attr->attributesMap.get(attr)).collect(Collectors.toList());

                // Get filters
                List<AbstractFilter> preFilters = preFilterModels.stream().map(modelName->preFilterModelMap.get(modelName)).collect(Collectors.toList());
                List<AbstractFilter> postFilters = postFilterModels.stream().map(modelName->postFilterModelMap.get(modelName)).collect(Collectors.toList());
                // Update filters based on params
                Arrays.asList(preFilters,postFilters).stream()
                        .flatMap(filterList->filterList.stream())
                        .forEach(filter->filter.extractRelevantInformationFromParams(req.queryMap()));

                // Get value models
                List<ValueAttr> evaluators = valueModels.stream().map(modelName->valueModelMap.get(modelName)).collect(Collectors.toList());

                // Get similarity model
                AbstractSimilarityModel finderPrototype = similarityModelMap.get(similarityModel+"_"+portfolioType.toString());

                AbstractSimilarityModel firstFinder = searchEntireDatabase ? finderPrototype : finderPrototype.duplicateWithScope(inputsToSearchIn);
                if (firstFinder == null || firstFinder.numItems() == 0) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Unable to find any results to search in.");
                    return null;
                }

                AbstractSimilarityModel secondFinder = finderPrototype.duplicateWithScope(inputsToSearchFor);
                if (secondFinder == null || secondFinder.numItems() == 0) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Unable to find any of the search inputs.");
                    return null;
                }

                // Run similarity models
                System.out.println("Running model...");
                PortfolioList portfolioList = runPatentFinderModel(firstFinder,secondFinder,limit,preFilters);

                // Apply attributes
                System.out.println("Applying attributes...");
                attributes.forEach(attribute->{
                    portfolioList.applyAttribute(attribute);
                });

                // Apply value models
                evaluators.forEach(valueModel->{
                    portfolioList.applyAttribute(valueModel);
                });

                System.out.println("Initializing portfolio...");
                portfolioList.init(comparator,limit);

                System.out.println("Applying post filters...");
                // Run filters
                postFilters.forEach(filter->{
                    portfolioList.applyFilter(filter);
                });

                System.out.println("Applying technology values...");
                // Apply technology values
                technologies.forEach(technology->{
                    portfolioList.applyAttribute(new SpecificTechnologyEvaluator(technology,getTechTagger()));
                });

                System.out.println("Rendering table...");
                return tableFromPatentList(portfolioList.getItemList(),Arrays.asList(Arrays.asList(Constants.NAME,Constants.SIMILARITY),valueModels,itemAttributes).stream().flatMap(list->list.stream()).collect(Collectors.toList()));

            } catch(Exception e) {
                System.out.println(e.getClass().getName()+": "+e.getMessage());
                res.redirect("/candidate_set_models");
                req.session().attribute("message","Error occured: "+e.toString());
                return null;
            }

        });

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
                        script().attr("src","https://ajax.googleapis.com/ajax/libs/jquery/3.0.0/jquery.min.js"),
                        script().attr("src","http://code.highcharts.com/highcharts.js"),
                        script().attr("src","/js/customEvents.js"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().with(
                        div().attr("style", "width:80%; padding: 2% 10%;").with(
                                a().attr("href", "/").with(
                                        img().attr("src", "/images/brand.png")
                                ),
                                hr(),
                                h3("Artificial Intelligence Tools (Beta)"),
                                hr(),
                                h4(message),
                                form,
                                div().withId("results"),
                                br(),
                                br(),
                                br()
                        )
                )
        );
    }

    static Tag formScript(String formId, String url, String buttonText, boolean ajax) {
        return script().withText(ajax?
                ("$(document).ready(function() { "
                          + "$('#"+formId+"').submit(function(e) {"
                            + "$('#"+formId+"-button').attr('disabled',true).text('"+buttonText+"ing...');"
                            + "var url = '"+url+"'; "
                            + "var tempScrollTop = $(window).scrollTop();"
                            + "$.ajax({"
                            + "  type: 'POST',"
                            + "  dataType: 'json',"
                            + "  url: url,"
                            + "  data: $('#"+formId+"').serialize(),"
                            + "  complete: function(jqxhr,status) {"
                            + "    $('#"+formId+"-button').attr('disabled',false).text('"+buttonText+"');"
                            + "    $(window).scrollTop(tempScrollTop);"
                            + "  },"
                            + "  success: function(data) { "
                            + "    $('#results').html(data.message); "
                            + "  }"
                            + "});"
                            + "e.preventDefault(); "
                          + "});"
                        + "});") : (
                "$(document).ready(function() { "
                        + "$('#"+formId+"').attr('action','"+url+"');"
                        + "$('#"+formId+"').attr('method','POST');"
                + "});" )

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

    private static Tag gatherTechnologySelect() {
        return div().attr("style","max-height: 200px; overflow-y: auto;").with(getTechTagger().getClassifications().stream().map(technology-> {
            return div().with(label(technology),input().withType("checkbox").withName(TECHNOLOGIES_TO_SEARCH_FOR_FIELD).withValue(technology));
        }).collect(Collectors.toList()));
    }

    private static Tag candidateSetModelsForm() {
        return div().with(
                formScript(SELECT_BETWEEN_CANDIDATES_FORM_ID, "/similar_candidate_sets", "Search", false),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h2("Knowledge Base"),
                                                form().withId(SELECT_BETWEEN_CANDIDATES_FORM_ID).with(
                                                        label("Similarity Model"),br(),select().withName(SIMILARITY_MODEL_FIELD).with(
                                                                option().withValue(Constants.PARAGRAPH_VECTOR_MODEL).attr("selected","true").withText("Paragraph Vector Model"),
                                                                option().withValue(Constants.SIM_RANK_MODEL).withText("SimRank Model"),
                                                                option().withValue(Constants.CPC_MODEL).withText("CPC Model")
                                                        ),hr(),
                                                        expandableDiv("Search Within",
                                                                h4("Search Within"),
                                                                label("Leave Blank To Search Full Database"),br(),
                                                                label("Custom Patent List (1 per line)"),br(),
                                                                textarea().withName(PATENTS_TO_SEARCH_IN_FIELD),br(),
                                                                label("Custom Assignee List (1 per line)"),br(),
                                                                textarea().withName(ASSIGNEES_TO_SEARCH_IN_FIELD),br()
                                                        ),hr(),
                                                        expandableDiv("Search For",
                                                                h4("Search For"),
                                                                select().withName(SEARCH_TYPE_FIELD).with(
                                                                        Arrays.stream(PortfolioList.Type.values()).map(type->{
                                                                            ContainerTag option = option(type.toString()).withValue(type.toString());
                                                                            if(type.equals(PortfolioList.Type.patents)) option=option.attr("selected","selected");
                                                                            return option;
                                                                        }).collect(Collectors.toList())
                                                                ),br(),
                                                                h4("With Similarity To"),
                                                                label("Patents (1 per line)"),br(),
                                                                textarea().withName(PATENTS_TO_SEARCH_FOR_FIELD), br(),
                                                                label("Assignees (1 per line)"),br(),
                                                                textarea().withName(ASSIGNEES_TO_SEARCH_FOR_FIELD), br(),
                                                                label("Gather Technology"),br(),
                                                                gatherTechnologySelect(),br()
                                                        ), hr(),
                                                        expandableDiv("Data Attributes",h4("Select Data Fields to capture"),div().with(
                                                                attributesMap.keySet().stream().map(key-> {
                                                                    return div().with(label(humanAttributeFor(key)),input().withType("checkbox").withName(ATTRIBUTES_ARRAY_FIELD).withValue(key));
                                                                }).collect(Collectors.toList()))
                                                        ),hr(),
                                                        expandableDiv("Valuation Models",h4("Select Value Fields to capture"),div().with(
                                                                valueModelMap.keySet().stream().map(key-> {
                                                                    return div().with(label(humanAttributeFor(key)),input().withType("checkbox").withName(VALUE_MODELS_ARRAY_FIELD).withValue(key));
                                                                }).collect(Collectors.toList()))
                                                        ),hr(),
                                                        expandableDiv("Filters",h4("Select applicable Filters"),div().with(
                                                                Arrays.asList(new Pair<>(preFilterModelMap,PRE_FILTER_ARRAY_FIELD),new Pair<>(postFilterModelMap,POST_FILTER_ARRAY_FIELD)).stream().flatMap(pair-> {
                                                                    return pair._1.entrySet().stream().map(e->{
                                                                        String key = e.getKey();
                                                                        AbstractFilter filter = e.getValue();
                                                                        return div().with(
                                                                                label(humanAttributeFor(key)),
                                                                                input().withType("checkbox").withName(pair._2).withValue(key),
                                                                                filter.getOptionsTag()==null? div():filter.getOptionsTag());
                                                                    });
                                                                }).collect(Collectors.toList()))
                                                        ),hr(),
                                                        button("Search").withId(SELECT_BETWEEN_CANDIDATES_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                )
                        )
                )
        );
    }


    static List<String> extractArray(Request req, String param) {
        String[] array = req.queryParamsValues(param);
        if(array!=null) return Arrays.stream(array).collect(Collectors.toList());
        else return Collections.emptyList();
    }

    static String extractString(Request req, String param, String defaultVal) {
        return extractString(req.queryMap(),param,defaultVal);
    }

    public static String extractString(QueryParamsMap paramsMap, String param, String defaultVal) {
        if(paramsMap.value(param)!=null&&paramsMap.value(param).trim().length()>0) {
            return paramsMap.value(param);
        } else {
            return defaultVal;
        }
    }

    static int extractInt(Request req, String param, int defaultVal) {
        try {
            return Integer.valueOf(req.queryParams(param));
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return defaultVal;
        }
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
        try {
            return (req.queryParams(param)==null||!req.queryParams(param).startsWith("on")) ? false : true;
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
        LeadDevelopmentUI.setupServer();
        CompanyPortfolioProfileUI.setupServer();
        TechTaggerUI.setupServer();
        GatherClassificationServer.StartServer();
        PatentToolsServer.setup();
        ValuationServer.setupServer();
        System.out.println("Finished starting server.");
    }
}
