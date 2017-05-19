package server;

import similarity_models.AbstractSimilarityModel;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import com.google.gson.Gson;
import dl4j_neural_nets.tools.MyPreprocessor;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import seeding.Constants;
import seeding.Database;
import seeding.GetEtsiPatentsList;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.value.ValueAttr;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.attributes.value.*;
import ui_models.filters.*;
import ui_models.portfolios.items.AbstractAssignee;
import ui_models.portfolios.items.AbstractPatent;
import server.tools.SimpleAjaxMessage;
import excel.ExcelHandler;
import ui_models.portfolios.items.Item;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Session;
import tools.ClassCodeHandler;
import ui_models.portfolios.PortfolioList;
import seeding.patent_view_api.PatentAPIHandler;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    public static SimilarPatentFinder globalFinder;
    public static SimilarPatentFinder assigneeFinder;
    private static final String SELECT_BETWEEN_CANDIDATES_FORM_ID = "select-between-candidates-form";


    private static ClassificationAttr tagger;

    protected static ParagraphVectors paragraphVectors;
    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    static Map<String,ValueAttr> modelMap = new HashMap<>();

    private static Map<String,String> humanParamMap = Item.getHumanAttrToJavaAttrMap();
    private static Map<String,String> humanFilterMap = Item.getHumanFilterToJavaFilterMap();
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static WeightLookupTable<VocabWord> getLookupTable() {
        if(paragraphVectors==null) loadLookupTable();
        return paragraphVectors.getLookupTable();
    }

    public static ClassificationAttr getTagger() {
        return tagger;
    }
    public static void loadLookupTable() {
        if(paragraphVectors!=null)return;
        boolean testing = false;
        try {
            if(testing==true) {
                paragraphVectors = ParagraphVectorModel.loadTestParagraphsModel();
            } else {
                paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
            }
        } catch(Exception e) {
            e.printStackTrace();
            //paragraphVectors = ParagraphVectorModel.loadAllClaimsModel();
            System.out.println("DEFAULTING TO OLDER MODEL");
        }
    }

    public static VocabCache<VocabWord> getVocabCache() {
        if(paragraphVectors==null)loadLookupTable();
        return paragraphVectors.getVocab();
    }

    static void loadValueModels() {
        if(modelMap.isEmpty()) {
            try {
                modelMap.put("overallValue", new OverallEvaluator());
                modelMap.put("pageRankValue", new PageRankEvaluator());
                modelMap.put("marketValue",new MarketEvaluator());
                modelMap.put("citationValue", new CitationEvaluator());
                modelMap.put("claimValue",new ClaimEvaluator());
                modelMap.put("assetsPurchased", new AssetsPurchasedEvaluator());
                modelMap.put("assetsSold", new AssetsSoldEvaluator());
                modelMap.put("technologyValue",new TechnologyEvaluator());
                modelMap.put("compDBAssetsPurchased", new AssetsPurchasedEvaluator());
                modelMap.put("compDBAssetsSold", new AssetsSoldEvaluator());
                modelMap.put("largePortfolios", new PortfolioSizeEvaluator());
                modelMap.put("smallPortfolios", new SmallPortfolioSizeEvaluator());
                modelMap.put("transactionValue",new TransactionEvaluator());
                modelMap.put("assetFamilyValue", new AssetFamilyEvaluator());
                modelMap.put("pendencyValue",new PendencyEvaluator());
                modelMap.put("claimRatioValue",new ClaimRatioEvaluator());
                modelMap.put("classValue",new ClassEvaluator());
                modelMap.put("priorArtValue", new PriorArtEvaluator());
                modelMap.put("maintenanceFeeValue",new MaintenanceFeeEvaluator());
                modelMap.put("claimLengthValue", new ClaimLengthEvaluator());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void loadBaseFinder() {
        try {
            globalFinder =  new SimilarPatentFinder(Database.getValuablePatents(),"** ALL PATENTS **",paragraphVectors.lookupTable());
            assigneeFinder = new SimilarPatentFinder(Database.getAssignees(),"** ALL ASSIGNEES **",paragraphVectors.lookupTable());
            tagger = TechTaggerNormalizer.getDefaultTechTagger();
            // value model
            loadValueModels();

        } catch(Exception e) {
            e.printStackTrace();
        }
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
                // get input data
                Set<String> patentsToSearchFor = new HashSet<>(preProcess(extractString(req, "patents", ""), "\\s+", "[^0-9]"));
                List<String> wordsToSearchFor = preProcess(extractString(req, "words", ""), "\\s+", "[^a-zA-Z0-9 ]");
                Set<String> assigneesToSearchFor = new HashSet<>();
                preProcess(extractString(req, "assignees", "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]").forEach(assignee -> assigneesToSearchFor.add(assignee));
                Set<String> classCodesToSearchFor = new HashSet<>();
                preProcess(extractString(req, "class_codes", "").toUpperCase(), "\n", null).stream().map(classCode -> ClassCodeHandler.convertToLabelFormat(classCode)).forEach(cpc -> classCodesToSearchFor.add(cpc));
                String searchType = extractString(req, "search_type", "patents");
                int limit = extractInt(req, "limit", 10);
                PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(searchType);

                if(req.queryParamsValues("dataAttributes[]")==null) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Please choose some data fields to report.");
                    return null;
                }

                List<String> attributes = Arrays.stream(req.queryParamsValues("dataAttributes[]")).collect(Collectors.toList());

                boolean includeSubclasses = extractBool(req, "includeSubclasses");
                boolean allowResultsFromOtherCandidateSet = extractBool(req, "allowResultsFromOtherCandidateSet");
                boolean searchEntireDatabase = extractBool(req, "search_all");
                int assigneePortfolioLimit = extractInt(req, "portfolio_limit", -1);
                boolean mergeSearchInput = extractBool(req, "merge_search_input");
                boolean removeGatherPatents = extractBool(req, "remove_gather_patents");

                // pre data
                Collection<String> classCodesToSearchIn = new HashSet<>();
                preProcess(extractString(req, "custom_class_code_list", "").toUpperCase(), "\n", null).stream()
                        .map(classCode -> ClassCodeHandler.convertToLabelFormat(classCode)).forEach(cpc -> classCodesToSearchIn.addAll(Database.subClassificationsForClass(cpc)));
                Collection<String> patentsToSearchIn = new HashSet<>(preProcess(extractString(req, "custom_patent_list", ""), "\\s+", "[^0-9]"));
                List<String> customAssigneeList = preProcess(extractString(req, "custom_assignee_list", "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
                Set<String> labelsToExclude = new HashSet<>();


                SimilarPatentFinder firstFinder = getFirstPatentFinder(labelsToExclude,customAssigneeList,patentsToSearchIn,classCodesToSearchIn,searchEntireDatabase,includeSubclasses,allowResultsFromOtherCandidateSet,searchType,patentsToSearchFor,assigneesToSearchFor,classCodesToSearchFor);

                if (firstFinder == null || firstFinder.getPatentList().size() == 0) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Unable to find any results to search in.");
                    return null;
                }

                List<SimilarPatentFinder> secondFinders = getSecondPatentFinder(mergeSearchInput,wordsToSearchFor,patentsToSearchFor,assigneesToSearchFor,classCodesToSearchFor);

                if (secondFinders.isEmpty() || secondFinders.stream().collect(Collectors.summingInt(finder -> finder.getPatentList().size())) == 0) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Unable to find any of the search inputs.");
                    return null;
                }

                HttpServletResponse raw = res.raw();
                res.header("Content-Disposition", "attachment; filename=download.xls");
                res.type("application/force-download");


                // get more detailed params
                String clientName = extractString(req, "client", "");
                double threshold = extractThreshold(req);
                Set<String> badAssignees = new HashSet<>();
                preProcess(extractString(req, "assigneeFilter", "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]").forEach(assignee -> badAssignees.addAll(Database.possibleNamesForAssignee(assignee)));
                Set<String> badAssets = new HashSet<>(preProcess(req.queryParams("assetFilter"), "\\s+", "US"));
                Set<String> highlightAssignees = new HashSet<>();
                preProcess(req.queryParams("assigneeHighlighter"), "\n", "[^a-zA-Z0-9 ]").forEach(assignee -> highlightAssignees.addAll(Database.possibleNamesForAssignee(assignee)));
                String title = extractString(req, "title", "");

                labelsToExclude.addAll(badAssets);
                labelsToExclude.addAll(badAssignees);


                if(removeGatherPatents) {
                    labelsToExclude.addAll(Database.getGatherPatents());
                }

                Collection<? extends AbstractFilter> preFilters = Arrays.asList(
                        new ThresholdFilter(threshold),
                        new AssigneeFilter(badAssignees),
                        new LabelFilter(labelsToExclude)
                );

                System.out.println("Running model");
                PortfolioList portfolioList = runPatentFinderModel(firstFinder, secondFinders, portfolioType, limit, preFilters);

                Collection<? extends AbstractFilter> postFilters = Arrays.asList(
                        new PortfolioSizeFilter(assigneePortfolioLimit)
                );

                System.out.println("Starting post filters");

                // Post filters
                postFilters.forEach(filter->{
                    portfolioList.applyFilter(filter);
                });


                System.out.println("Starting model map");

                modelMap.forEach((key,model)->{
                    try {
                        if (attributes.contains(key) && model != null) {
                            evaluateModel(model, portfolioList.getPortfolio(), key, portfolioType);
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                        System.out.println("Exception with: "+key);
                    }
                });

                System.out.println("Finished model map");

                Comparator<Item> comparator = Item.similarityComparator();

                // Handle overall value
                if(attributes.contains("overallValue")) {
                    comparator = Item.valueComparator();
                }

                {
                    String keywordsToRequire = extractString(req, "required_keywords", null);
                    if (keywordsToRequire != null) {
                        String[] keywords = keywordsToRequire.toLowerCase().replaceAll("[^a-z %\\n]", " ").split("\\n");
                        if (keywords.length > 0) {
                            Set<String> patents = Database.patentsWithAllKeywords(portfolioList.getTokens(), keywords);
                            portfolioList.setPortfolio(portfolioList.getPortfolio().stream()
                                    .filter(patent -> patents.contains(patent.getName()))
                                    .collect(Collectors.toList())
                            );
                        }
                    }
                }
                {
                    String keywordsToAvoid = extractString(req, "avoided_keywords", null);
                    if (keywordsToAvoid != null) {
                        String[] keywords = keywordsToAvoid.toLowerCase().replaceAll("[^a-z %\\n]", " ").split("\\n");
                        if (keywords.length > 0) {
                            Set<String> patents = Database.patentsWithKeywords(portfolioList.getTokens(), keywords);
                            portfolioList.setPortfolio(portfolioList.getPortfolio().stream()
                                    .filter(patent -> !patents.contains(patent.getName()))
                                    .collect(Collectors.toList())
                            );
                        }
                    }
                }
                System.out.println("Init portfolio");
                portfolioList.init(comparator,limit);
                System.out.println("Finished init portfolio");

                boolean includeCoverPage = extractBool(req,"include_cover_page");

                // contact info
                String EMLabel = extractContactInformation(req, "label1", Constants.DEFAULT_EM_LABEL);
                String EMName = extractContactInformation(req, "cname1", Constants.DEFAULT_EM_NAME);
                String EMTitle = extractContactInformation(req, "title1", Constants.DEFAULT_EM_TITLE);
                String EMPhone = extractContactInformation(req, "phone1", Constants.DEFAULT_EM_PHONE);
                String EMEmail = extractContactInformation(req, "email1", Constants.DEFAULT_EM_EMAIL);
                String SAMName = extractContactInformation(req, "cname2", Constants.DEFAULT_SAM_NAME);
                String SAMTitle = extractContactInformation(req, "title2", Constants.DEFAULT_SAM_TITLE);
                String SAMPhone = extractContactInformation(req, "phone2", Constants.DEFAULT_SAM_PHONE);
                String SAMLabel = extractContactInformation(req, "label2", Constants.DEFAULT_SAM_LABEL);
                String SAMEmail = extractContactInformation(req, "email2", Constants.DEFAULT_SAM_EMAIL);
                String[] EMData = new String[]{EMLabel, EMName, EMTitle, EMPhone, EMEmail};
                String[] SAMData = new String[]{SAMLabel, SAMName, SAMTitle, SAMPhone, SAMEmail};
                System.out.println("Writing spreadsheet");
                try {
                    ExcelHandler.writeDefaultSpreadSheetToRaw(raw, highlightAssignees, title, clientName, EMData, SAMData, attributes, includeCoverPage, portfolioList);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return raw;
            } catch(Exception e) {
                res.redirect("/candidate_set_models");
                req.session().attribute("message","Error occured: "+e.toString());
                return null;
            }

        });

    }

    static SimilarPatentFinder getFirstPatentFinder(Set<String> labelsToExclude, Collection<String> customAssigneeList, Collection<String> patentsToSearchIn, Collection<String> classCodesToSearchIn, boolean searchEntireDatabase, boolean includeSubclasses, boolean allowResultsFromOtherCandidateSet, String searchType, Collection<String> patentsToSearchFor, Collection<String> assigneesToSearchFor, Collection<String> classCodesToSearchFor) {
        SimilarPatentFinder firstFinder;
        if (!allowResultsFromOtherCandidateSet) {
            if(searchType.equals("class_codes")){
                classCodesToSearchFor.forEach(code->labelsToExclude.add(code));
            }
            if(searchType.equals("assignees")) {
                assigneesToSearchFor.forEach(assignee -> {
                    labelsToExclude.addAll(Database.possibleNamesForAssignee(assignee));
                });
            }
            labelsToExclude.addAll(patentsToSearchFor);
        }

        // get first finder
        if (searchEntireDatabase) {
            if (searchType.equals("patents")) {
                firstFinder = globalFinder;
            } else if (searchType.equals("assignees")) {
                firstFinder = assigneeFinder;
            } else {
                return null;
            }
        } else {
            // dependent on searchType
            if (searchType.equals("patents")) {
                customAssigneeList.forEach(assignee -> patentsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
                classCodesToSearchIn.forEach(cpc -> patentsToSearchIn.addAll(includeSubclasses?Database.selectPatentNumbersFromClassAndSubclassCodes(cpc):Database.selectPatentNumbersFromExactClassCode(cpc)));
                firstFinder = new SimilarPatentFinder(patentsToSearchIn, null, paragraphVectors.getLookupTable());
            } else if (searchType.equals("assignees")) {
                Collection<String> assigneesToSearchIn = new HashSet<>();
                customAssigneeList.forEach(assignee -> {
                    assigneesToSearchIn.addAll(Database.possibleNamesForAssignee(assignee));
                });
                classCodesToSearchIn.forEach(cpc -> patentsToSearchIn.addAll(includeSubclasses?Database.selectPatentNumbersFromClassAndSubclassCodes(cpc):Database.selectPatentNumbersFromExactClassCode(cpc)));
                patentsToSearchIn.forEach(patent -> Database.assigneesFor(patent).forEach(assignee -> assigneesToSearchIn.addAll(Database.possibleNamesForAssignee(assignee))));
                firstFinder = new SimilarPatentFinder(assigneesToSearchIn, null, paragraphVectors.getLookupTable());
            } else if (searchType.equals("class_codes")) {
                customAssigneeList.forEach(assignee -> patentsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
                patentsToSearchIn.forEach(patent -> classCodesToSearchIn.addAll(Database.classificationsFor(patent)));
                firstFinder = new SimilarPatentFinder(classCodesToSearchIn, null, paragraphVectors.getLookupTable());
            } else {
                return null;
            }
        }
        return firstFinder;
    }

    static List<SimilarPatentFinder> getSecondPatentFinder(boolean mergeSearchInput, Collection<String>... toSearchFor) {
        List<SimilarPatentFinder> patentFinders = new ArrayList<>();
        Set<String> stuffToSearchFor = new HashSet<>();
        Arrays.stream(toSearchFor).forEach(collection->stuffToSearchFor.addAll(collection));

        if(mergeSearchInput) {
            patentFinders.add(new SimilarPatentFinder(stuffToSearchFor,null,paragraphVectors.getLookupTable()));
        } else {
            stuffToSearchFor.forEach(patent -> patentFinders.add(new SimilarPatentFinder(Arrays.asList(patent), patent, paragraphVectors.getLookupTable())));
        }
        return patentFinders;
    }

    public static PortfolioList runPatentFinderModel(SimilarPatentFinder firstFinder, List<? extends AbstractSimilarityModel> secondFinders, PortfolioList.Type portfolioType, int resultLimit, Collection<? extends AbstractFilter> preFilters) {
        List<PortfolioList> portfolioLists = new ArrayList<>();
        try {
            List<PortfolioList> similar = firstFinder.similarFromCandidateSets(secondFinders, portfolioType, resultLimit/secondFinders.size(), preFilters);
            portfolioLists.addAll(similar);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("... while running patent finder model.");
            throw new RuntimeException(e.getMessage());
        }
        System.out.println("SIMILAR PATENTS FOUND!!!");
        return mergePatentLists(portfolioLists, portfolioType, resultLimit);
    }

    private static PortfolioList mergePatentLists(List<PortfolioList> portfolioLists, PortfolioList.Type portfolioType, int totalLimit) {
            Map<String, Item> map = new HashMap<>();
            portfolioLists.forEach(portfolioList -> {
                portfolioList.getPortfolio().forEach(item -> {
                    if (item.getName() == null || item.getName().length() == 0) return;
                    Item itemInMap = map.get(item.getName());
                    if (itemInMap!=null) {
                        itemInMap.appendTags(item.getTags());
                        itemInMap.setSimilarity(Math.max(item.getSimilarity(), itemInMap.getSimilarity()));
                    } else {
                        map.put(item.getName(), item);
                    }
                });
            });
            try {
                List<Item> merged = map.values().stream().sorted(Comparator.reverseOrder()).limit(totalLimit).collect(Collectors.toList());
                PortfolioList portfolio = new PortfolioList(merged,portfolioType);
                return portfolio;
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("While merging");
                throw new RuntimeException("while merging");
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
                                                                humanParamMap.entrySet().stream().map(e-> {
                                                                    return div().with(label(e.getKey()),input().withType("checkbox").withName("dataAttributes[]").withValue(e.getValue()).attr("checked","checked"));
                                                                }).collect(Collectors.toList()))
                                                        ),hr(),expandableDiv("Filters",h3("Select applicable Filters"),div().with(
                                                                humanFilterMap.entrySet().stream().map(e-> {
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

    private static String reasonablySizedString(String longString) {
        int tooBig = 22;
        if(longString.length()>tooBig) {
            longString=longString.substring(0,tooBig)+ "...";
        }
        return longString;
    }

    private static double extractThreshold(Request req) {
        try {
            return Double.valueOf(req.queryParams("threshold"));
        } catch(Exception e) {
            System.out.println("No threshold parameter specified... using default");
            return 0.70;
        }
    }


    private static String extractContactInformation(Request req, String param, String defaultAnswer) {
        try {
            if(req.queryParams(param)==null||req.queryParams(param).trim().length()==0) return defaultAnswer;
            return req.queryParams(param);
        } catch(Exception e) {
            System.out.println("No "+param+"+ parameter specified... using default => "+defaultAnswer);
            return defaultAnswer;
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
        System.out.println("Starting to load lookup table...");
        loadLookupTable();
        System.out.println("Finished loading lookup table.");
        System.out.println("Starting to load base finder...");
        loadBaseFinder();
        System.out.println("Finished loading base finder.");
        System.out.println("Starting server...");
        server();
        LeadDevelopmentUI.setupServer();
        CompanyPortfolioProfileUI.setupServer();
        TechTaggerUI.setupServer();
        GatherClassificationServer.StartServer();
        PatentToolsServer.setup();
        System.out.println("Finished starting server.");
    }
}
