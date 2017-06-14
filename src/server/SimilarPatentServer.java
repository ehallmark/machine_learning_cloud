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
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.value.ValueAttr;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.attributes.value.*;
import ui_models.filters.*;
import excel.ExcelHandler;
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
                valueModelMap.put("overallValue", new OverallEvaluator());
                valueModelMap.put("compDBAssetsPurchased", new AssetsPurchasedEvaluator());
                valueModelMap.put("compDBAssetsSold", new AssetsSoldEvaluator());
                valueModelMap.put("largePortfolios", new PortfolioSizeEvaluator());
                valueModelMap.put("smallPortfolios", new SmallPortfolioSizeEvaluator());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadFilterModels() {
        if(filterModelMap.isEmpty()) {
            try {
                filterModelMap.put("thresholdFilter",new ThresholdFilter(0d));
                filterModelMap.put("portfolioSizeFilter",new PortfolioSizeFilter(100));
                filterModelMap.put("expirationFilter",new ExpirationFilter());

            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadSimilarityModels() {
        if(similarityModelMap.isEmpty()) {
            try {
                similarityModelMap.put("pvector_patents",new SimilarPatentFinder(Database.getValuablePatents(), "** Paragraph Vector Model **"));
                similarityModelMap.put("pvector_assignees", new SimilarPatentFinder(Database.getAssignees(), "** Paragraph Vector Model **"));
                similarityModelMap.put("sim_rank_patents", new SimRankSimilarityModel("** SimRank Model **", Database.getValuablePatents()));
                similarityModelMap.put("cpc_patents", new CPCSimilarityFinder(Database.getValuablePatents(), "** CPC Model **"));
                similarityModelMap.put("cpc_assignees",new CPCSimilarityFinder(Database.getAssignees(), "** CPC Model **"));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadTechTaggerModel() {
        if(tagger==null)tagger = TechTaggerNormalizer.getDefaultTechTagger();
    }

    static void evaluateModel(ValueAttr model, Collection<Item> portfolio, String valueParamType, PortfolioList.Type type) {
        System.out.println("Starting to evaluate model: "+valueParamType);
        for (Item item : portfolio) {
            double score = model.attributesFor(PortfolioList.asList(item.getName(),type),1);
            item.setValue(valueParamType,score);
        }
        System.out.println("Finished "+valueParamType);
    }

    static String getAndRemoveMessage(Session session) {
        String message = session.attribute("message");
        if(message!=null)session.removeAttribute("message");
        return message;
    }

    private static Tag homePage() {
        return div().with(
                h3().with(
                        a("Portfolio Comparison").withHref("/candidate_set_models")
                ),h3().with(
                        a("Company Profiler").withHref("/company_profile")
                ),
                h3().with(
                        a("Lead Development").withHref("/lead_development")
                ),
                h3().with(
                        a("Tech Tagger").withHref("/tech_tagger")
                ),
                h3().with(
                        a("Asset Valuation").withHref("/asset_valuation")
                ),
                h3().with(
                        a("Additional Patent Tools").withHref("/patent_toolbox")
                )
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

                // get input data
                // TODO Handle Gather Technolgoy as input
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
                List<String> filterModels = Arrays.stream(req.queryParamsValues("filterModels[]")).collect(Collectors.toList());
                List<String> techTaggerModels = Arrays.stream(req.queryParamsValues("techTaggerModels[]")).collect(Collectors.toList());
                List<String> itemAttributes = Arrays.stream(req.queryParamsValues("itemAttributes[]")).collect(Collectors.toList());

                AbstractSimilarityModel finderPrototype = similarityModelMap.get(similarityModel+"_"+portfolioType.toString());

                AbstractSimilarityModel firstFinder = searchEntireDatabase ? finderPrototype : finderPrototype.duplicateWithScope(inputsToSearchIn);
                if (firstFinder == null || firstFinder.numItems() == 0) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Unable to find any results to search in.");
                    return null;
                }

                AbstractSimilarityModel secondFinder = finderPrototype.duplicateWithScope(inputsToSearchIn);
                if (secondFinder == null || secondFinder.numItems() == 0) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Unable to find any of the search inputs.");
                    return null;
                }

                // Get filters
                List<AbstractFilter> filters = new ArrayList<>();
                filterModels.forEach(filterModel->{
                    AbstractFilter filter = filterModelMap.get(filterModel);
                    filters.add(filter);
                });

                // Run similarity models
                PortfolioList portfolioList = runPatentFinderModel(firstFinder,secondFinder,limit,filters);
                String comparator = extractString(req, "comparator", "similarity");
                System.out.println("Init portfolio");
                portfolioList.init(comparatorMap.get(comparator),limit);
                System.out.println("Finished init portfolio");

                // Run taggers and value models


                return tableFromPatentList(portfolioList.getPortfolio(),Arrays.asList(valueModels,techTaggerModels,itemAttributes).stream().flatMap(list->list.stream()).collect(Collectors.toList()));

            } catch(Exception e) {
                res.redirect("/candidate_set_models");
                req.session().attribute("message","Error occured: "+e.toString());
                return null;
            }

        });

    }

    private static Tag tableFromPatentList(List<Item> items, List<String> attributes) {
        return table().with(
                thead().with(
                        tr().with(
                                attributes.stream().map(attr->th(Item.humanAttributeFor(attr))).collect(Collectors.toList())
                        )
                ),tbody().with(
                        items.stream().map(item->tr().with(
                                item.getDataAsRow(attributes).getCells().stream().map(cell->cell==null?td(""):td(cell.getContent().toString())).collect(Collectors.toList())
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

    private static List<String> preProcess(String toSplit, String delim, String toReplace) {
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
        if(req.queryParams(param)!=null&&req.queryParams(param).trim().length()>0) {
            return req.queryParams(param);
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
