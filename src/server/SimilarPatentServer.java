package server;

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
import tools.ClassCodeHandler;
import ui_models.portfolios.PortfolioList;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    private static final String SELECT_BETWEEN_CANDIDATES_FORM_ID = "select-between-candidates-form";

    private static ClassificationAttr tagger;

    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    static Map<String,ValueAttr> valueModelMap = new HashMap<>();
    static Map<String,AbstractSimilarityModel> similarityModelMap = new HashMap<>();
    static Map<String,AbstractFilter> filterModelMap = new HashMap<>();
    static Map<String,AbstractAttribute> attributesMap = new HashMap<>();
    static Map<String,Comparator<Item>> comparatorMap = new HashMap<>();

    protected static Map<String,String> humanAttrToJavaAttrMap;
    protected static Map<String,String> javaAttrToHumanAttrMap;
    protected static Map<String,String> humanFilterToJavaFilterMap;
    protected static Map<String,String> javaFilterToHumanFilterMap;
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        comparatorMap.put("value",Item.valueComparator());
        comparatorMap.put("similarity",Item.similarityComparator());
        { // Attrs
            humanAttrToJavaAttrMap = new HashMap<>();
            humanAttrToJavaAttrMap.put("Asset", "name");
            humanAttrToJavaAttrMap.put("Similarity", "similarity");
            humanAttrToJavaAttrMap.put("Relevant Asset(s)", "relevantAssetsList");
            humanAttrToJavaAttrMap.put("Relevant Asset Count", "relevantAssetCount");
            humanAttrToJavaAttrMap.put("Total Asset Count", "totalAssetCount");
            humanAttrToJavaAttrMap.put("Assignee", "assignee");
            humanAttrToJavaAttrMap.put("Title", "title");
            humanAttrToJavaAttrMap.put("Primary Tag", "primaryTag");
            humanAttrToJavaAttrMap.put("AI Value", "overallValue");
            humanAttrToJavaAttrMap.put("Technology", "technology");
            humanAttrToJavaAttrMap.put("Assignee Entity Type", "assigneeEntityType");
            humanAttrToJavaAttrMap.put("Large Portfolio Size", "largePortfolios");
            humanAttrToJavaAttrMap.put("Small Portfolio Size", "smallPortfolios");
            humanAttrToJavaAttrMap.put("Assets Sold", "assetsSold");
            humanAttrToJavaAttrMap.put("Assets Purchased", "assetsPurchased");
            humanAttrToJavaAttrMap.put("CompDB Assets Sold", "compDBAssetsSold");
            humanAttrToJavaAttrMap.put("CompDB Assets Purchased", "compDBAssetsPurchased");

            // inverted version to get human readables back
            javaAttrToHumanAttrMap = new HashMap<>();
            humanAttrToJavaAttrMap.forEach((k, v) -> javaAttrToHumanAttrMap.put(v, k));
        }

        {
            // filters
            humanFilterToJavaFilterMap= new HashMap<>();
            humanFilterToJavaFilterMap.put("Portfolio Size Less Than","portfolioSizeLessThan");
            humanFilterToJavaFilterMap.put("Portfolio Size Greater Than","portfolioSizeGreaterThan");
            humanFilterToJavaFilterMap.put("Similarity Threshold","similarityThreshold");
            humanFilterToJavaFilterMap.put("Value Threshold","valueThreshold");
            humanFilterToJavaFilterMap.put("Only Japanese Assignees","japaneseOnly");
            humanFilterToJavaFilterMap.put("Exclude Japanese Assignees","removeJapanese");


            // inverted version to get human readables back
            javaFilterToHumanFilterMap = new HashMap<>();
            humanFilterToJavaFilterMap.forEach((k, v) -> javaFilterToHumanFilterMap.put(v, k));
        }
    }

    public static String humanAttributeFor(String attr) {
        if(javaAttrToHumanAttrMap.containsKey(attr))  {
            return javaAttrToHumanAttrMap.get(attr);
        } else {
            return attr;
        }
    }

    public static ClassificationAttr getTagger() {
        return tagger;
    }

    public static void initialize() {
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
        if(filterModelMap.isEmpty()) {
            try {
                filterModelMap.put(Constants.THRESHOLD_FILTER,new ThresholdFilter());
                filterModelMap.put(Constants.PORTFOLIO_SIZE_FILTER,new PortfolioSizeFilter());
                filterModelMap.put(Constants.EXPIRATION_FILTER,new ExpirationFilter());
                filterModelMap.put(Constants.LABEL_FILTER,new LabelFilter());
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

    public static void loadTechTaggerModel() {
        if(tagger==null)tagger = TechTaggerNormalizer.getDefaultTechTagger();
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
                // get meta parameters
                int limit = extractInt(req, "limit", 10);
                String searchType = extractString(req, "search_type", "patents");
                PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(searchType);
                String comparator = extractString(req, "comparator", "similarity");

                // get input data
                // TODO Handle Gather Technology as input
                Collection<String> inputsToSearchFor;
                if(portfolioType.equals(PortfolioList.Type.patents)) {
                    inputsToSearchFor=new HashSet<>(preProcess(extractString(req, "patents_to_search_for", ""), "\\s+", "[^0-9]"));
                    new HashSet<>(preProcess(extractString(req, "assignees_to_search_for", "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]")).forEach(assignee->{
                       inputsToSearchFor.addAll(Database.selectPatentNumbersFromAssignee(assignee));
                    });
                } else {
                    inputsToSearchFor=new HashSet<>(preProcess(extractString(req, "assignees_to_search_for", "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));
                }


                // Get scope of search
                Collection<String> inputsToSearchIn;
                if(portfolioType.equals(PortfolioList.Type.patents)) {
                    inputsToSearchIn=new HashSet<>(preProcess(extractString(req, "patents_to_search_in", ""), "\\s+", "[^0-9]"));
                    new HashSet<>(preProcess(extractString(req, "assignees_to_search_in", "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]")).forEach(assignee->inputsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));

                } else {
                    inputsToSearchIn=new HashSet<>(preProcess(extractString(req, "assignees_to_search_in", "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));
                }

                // Check whether to search entire database

                boolean searchEntireDatabase = inputsToSearchIn.isEmpty();

                // Get Models to use
                String similarityModel = extractString(req,"similarityModel", "pvector");
                List<String> valueModels = Arrays.stream(req.queryParamsValues("valueModels[]")).collect(Collectors.toList());
                List<String> preFilterModels = Arrays.stream(req.queryParamsValues("preFilterModels[]")).collect(Collectors.toList());
                List<String> postFilterModels = Arrays.stream(req.queryParamsValues("postFilterModels[]")).collect(Collectors.toList());
                List<String> itemAttributes = Arrays.stream(req.queryParamsValues("itemAttributes[]")).collect(Collectors.toList());

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

                // Get filters
                List<AbstractFilter> preFilters = preFilterModels.stream().map(modelName->filterModelMap.get(modelName)).collect(Collectors.toList());
                List<AbstractFilter> postFilters = postFilterModels.stream().map(modelName->filterModelMap.get(modelName)).collect(Collectors.toList());

                // Get value models
                List<ValueAttr> evaluators = valueModels.stream().map(modelName->valueModelMap.get(modelName)).collect(Collectors.toList());

                // Get tech tagger

                // Run similarity models
                System.out.println("Running model");
                PortfolioList portfolioList = runPatentFinderModel(firstFinder,secondFinder,limit,preFilters);

                System.out.println("Init portfolio");
                portfolioList.init(comparatorMap.get(comparator),limit);
                System.out.println("Finished init portfolio");

                // Run taggers and value models
                List<AbstractAttribute> attributes = itemAttributes.stream().map(attr->attributesMap.get(attr)).collect(Collectors.toList());

                attributes.forEach(attribute->{
                    portfolioList.applyAttribute(attribute);
                });

                // Run filters
                postFilters.forEach(filter->{
                    portfolioList.applyFilter(filter);
                });

                return tableFromPatentList(portfolioList.getItemList(),Arrays.asList(valueModels,itemAttributes).stream().flatMap(list->list.stream()).collect(Collectors.toList()));

            } catch(Exception e) {
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

    private static Tag coverPageForm() {
        return div().with(h3("Contact Info (primarily for the cover page)"),
                label("Include Cover Page? "),br(),input().withType("checkbox").withName("include_cover_page"),br(),
                label("Search Type (eg. 'Focused Search')"),br(),input().withType("text").withName("title").withValue("Custom Search"), br(),
                label("Client Name"),br(),input().withType("text").withName("client"), br(),
                label("Contact 1 Label"),br(),input().withType("text").withName("label1").withValue(Constants.DEFAULT_EM_LABEL), br(),
                label("Contact 1 Name"),br(),input().withType("text").withName("cname1").withValue(Constants.DEFAULT_EM_NAME), br(),
                label("Contact 1 Title"),br(),input().withType("text").withName("title1").withValue(Constants.DEFAULT_EM_TITLE), br(),
                label("Contact 1 Phone"),br(),input().withType("text").withName("phone1").withValue(Constants.DEFAULT_EM_PHONE), br(),
                label("Contact 1 Email"),br(),input().withType("text").withName("email1").withValue(Constants.DEFAULT_EM_EMAIL), br(),
                label("Contact 2 Label"),br(),input().withType("text").withName("label2").withValue(Constants.DEFAULT_SAM_LABEL), br(),
                label("Contact 2 Name"),br(),input().withType("text").withName("cname2").withValue(Constants.DEFAULT_SAM_NAME), br(),
                label("Contact 2 Title"),br(),input().withType("text").withName("title2").withValue(Constants.DEFAULT_SAM_TITLE), br(),
                label("Contact 2 Phone"),br(),input().withType("text").withName("phone2").withValue(Constants.DEFAULT_SAM_PHONE), br(),
                label("Contact 2 Email"),br(),input().withType("text").withName("email2").withValue(Constants.DEFAULT_SAM_EMAIL), br(),
                hr());
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

    private static Tag candidateSetModelsForm() {
        return div().with(
                formScript(SELECT_BETWEEN_CANDIDATES_FORM_ID, "/similar_candidate_sets", "Search", false),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h2("Knowledge Base"),
                                                form().withId(SELECT_BETWEEN_CANDIDATES_FORM_ID).with(
                                                        h4("Similarity Model (NEW)"),select().withName("similarity_model").with(
                                                                option().withValue("0").attr("selected","true").withText("PVecSim"),
                                                                option().withValue("1").withText("SimRank"),
                                                                option().withValue("2").withText("CPC-Sim")
                                                        ),h4("With relevance to"),
                                                        expandableDiv("Main Options", h3("Main options"),
                                                            h4("Search for results in"),
                                                            label("Entire Database"),input().withType("checkbox").withName("search_all"),
                                                            h4("Or by"),
                                                            label("Custom Patent List (1 per line)"),br(),
                                                            textarea().withName("custom_patent_list"),br(),
                                                            label("Custom Assignee List (1 per line)"),br(),
                                                            textarea().withName("custom_assignee_list"),br(),
                                                            label("Custom CPC Class Code List (1 per line)"),br(),
                                                            label("Example: F05D 01/233"),br(),
                                                            textarea().withName("custom_class_code_list"),
                                                            h4("To find"),select().withName("search_type").with(
                                                                    option().withValue("patents").attr("selected","true").withText("Patents"),
                                                                    option().withValue("assignees").withText("Assignees"),
                                                                    option().withValue("class_codes").withText("CPC Class Codes")
                                                            ),h4("With relevance to"),
                                                            label("Patents (1 per line)"),br(),textarea().withName("patents"),
                                                            br(),
                                                            label("Assignees (1 per line)"),br(),textarea().withName("assignees"),
                                                            br(),
                                                            label("CPC Class Codes (1 per line)"),br(),
                                                            label("Example: F05D 01/233"),br(),
                                                            textarea().withName("class_codes"), br(),
                                                            label("Arbitrary Text"),br(),
                                                            textarea().withName("words")
                                                        ),
                                                        hr(),
                                                        expandableDiv("Data Fields",h3("Select Data Fields to capture"),div().with(
                                                                humanAttrToJavaAttrMap.entrySet().stream().map(e-> {
                                                                    return div().with(label(e.getKey()),input().withType("checkbox").withName("dataAttributes[]").withValue(e.getValue()).attr("checked","checked"));
                                                                }).collect(Collectors.toList()))
                                                        ),hr(),expandableDiv("Filters",h3("Select applicable Filters"),div().with(
                                                                humanFilterToJavaFilterMap.entrySet().stream().map(e-> {
                                                                    return div().with(label(e.getKey()),input().withType("checkbox").withName("filters[]").withValue(e.getValue()));
                                                                }).collect(Collectors.toList()))
                                                        ),hr(),expandableDiv("Advanced Options",
                                                                h3("Advanced Options"),
                                                                label("Patent Limit"),br(),input().withType("text").withName("limit"), br(),
                                                                label("Merge Search Input?"),br(),input().withType("checkbox").withName("merge_search_input"),br(),
                                                                label("Remove Gather Assets?"),br(),input().withType("checkbox").withName("remove_gather_patents"),br(),
                                                                label("Relevance Threshold"),br(),input().withType("text").withName("threshold"),br(),
                                                                label("Portfolio Size Limit"),br(),input().withType("text").withName("portfolio_limit"), br(),
                                                                label("Allow Search Documents in Results?"),br(),input().withType("checkbox").withName("allowResultsFromOtherCandidateSet"),br(),
                                                                label("Include CPC Subclasses (if using CPC codes)?"),br(),input().withType("checkbox").withName("includeSubclasses"),br(),
                                                                label("Asset Filter (space separated)"),br(),textarea().withName("assetFilter"),br(),
                                                                label("Assignee Filter (1 per line)"),br(),textarea().withName("assigneeFilter"),br(),
                                                                label("Require keywords"),br(),textarea().withName("required_keywords"),br(),
                                                                label("Avoid keywords"),br(),textarea().withName("avoided_keywords"),br()), hr(),
                                                        expandableDiv("Cover Page Options",coverPageForm()),br(),hr(),br(),
                                                        button("Search").withId(SELECT_BETWEEN_CANDIDATES_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                )
                        )
                )
        );
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
