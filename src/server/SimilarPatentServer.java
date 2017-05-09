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
    private static final String ASSIGNEE_ASSET_COUNT_FORM_ID = "select-assignee-asset-count-form";
    private static final String ASSIGNEE_ASSETS_FORM_ID = "select-assignee-assets-form";
    private static final String PATENTS_FROM_ETSI_FORM_ID = "select-patents-from-etsi-form";
    private static final String CPC_FROM_ASSETS_FORM_ID = "select-cpc-from-assets-form";
    private static final String TITLE_FROM_ASSETS_FORM_ID = "select-title-from-assets-form";
    private static final String CPC_TO_ASSETS_FORM_ID = "select-cpc-to-assets-form";
    private static final String CPC_FREQUENCY_FROM_ASSETS_FORM_ID = "select-cpc-frequency-from-assets-form";
    private static final String TECH_PREDICTION_FROM_ASSETS_FORM_ID = "tech-from-assets-form";
    private static final String TECH_PREDICTION_FROM_ASSIGNEES_FORM_ID = "tech-from-assignees-form";
    private static final String TECH_PREDICTION_FROM_CPCS_FORM_ID = "tech-from-cpcs-form";

    private static ClassificationAttr tagger;

    protected static ParagraphVectors paragraphVectors;
    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    static Map<String,ValueAttr> modelMap = new HashMap<>();

    private static Map<String,String> humanParamMap = Item.getHumanAttrToJavaAttrMap();
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
        return paragraphVectors.getVocab();
    }

    static void loadValueModels() {
        try {
            modelMap.put("overallValue",new OverallEvaluator());
            modelMap.put("pageRankValue",new PageRankEvaluator());
            modelMap.put("assetsPurchased",new AssetsPurchasedEvaluator());
            modelMap.put("assetsSold",new AssetsSoldEvaluator());
            modelMap.put("compDBAssetsPurchased",new AssetsPurchasedEvaluator());
            modelMap.put("compDBAssetsSold",new AssetsSoldEvaluator());
            modelMap.put("largePortfolios",new PortfolioSizeEvaluator());
            modelMap.put("smallPortfolios",new SmallPortfolioSizeEvaluator());
        }catch(Exception e) {
            e.printStackTrace();
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
        for (Item item : portfolio) {
            double score = model.attributesFor(PortfolioList.asList(item.getName(),type),1);
            item.setValue(valueParamType,score);
        }
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
                        a("Additional Patent Tools").withHref("/patent_toolbox")
                )
        );
    }

    public static void server() {
        port(4568);
        // GET METHODS
        get("/", (req, res) -> templateWrapper(res, div().with(homePage(),hr()), getAndRemoveMessage(req.session())));
        get("/patent_toolbox", (req, res) -> templateWrapper(res, div().with(patentToolboxForm(), hr()), getAndRemoveMessage(req.session())));
        get("/candidate_set_models", (req, res) -> templateWrapper(res, div().with(candidateSetModelsForm(), hr()), getAndRemoveMessage(req.session())));

        // POST METHODS
        post("/assignee_asset_count", (req, res) -> {
            res.type("application/json");
            String assigneeStr = req.queryParams("assignee");
            if(assigneeStr==null||assigneeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));

            String[] assignees = assigneeStr.split(System.getProperty("line.separator"));
            if(assignees==null||assignees.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));
                Tag table = table().with(
                        thead().with(
                                tr().with(
                                        th("Assignee"),
                                        th("Approx. Asset Count")
                                )
                        ),
                        tbody().with(
                                Arrays.stream(assignees)
                                        .filter(assignee->!(assignee==null||assignee.isEmpty()))
                                        .map(assignee->tr().with(
                                            td(assignee),
                                            td(String.valueOf(Database.getAssetCountFor(assignee))))
                                ).collect(Collectors.toList())

                        )
                );
                return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/assignee_assets", (req, res) -> {
            res.type("application/json");
            String assigneeStr = req.queryParams("assignee");
            if(assigneeStr==null||assigneeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));

            String[] assignees = assigneeStr.split(System.getProperty("line.separator"));
            if(assignees==null||assignees.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));
            boolean usePatentsView = extractBool(req,"use_patents_view");
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Assignee"),
                                    th("Assets")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(assignees)
                                    .filter(assignee->!(assignee==null||assignee.isEmpty()))
                                    .map(assignee->tr().with(
                                            td(assignee),
                                            td(String.join(" ",usePatentsView ? PatentAPIHandler.requestPatentNumbersFromAssignee(assignee)
                                            : Database.selectPatentNumbersFromAssignee(assignee))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/tech_predictions", (req, res) -> {
            res.type("application/json");
            String type = req.queryParams("report_type");
            if(type==null||!Arrays.asList("patents","assignees","class_codes").contains(type)) {
                return new Gson().toJson(new SimpleAjaxMessage("Unknown report type"));
            }
            String asset = req.queryParams("item");
            if(asset==null||asset.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one input"));
            PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(type);
            String[] assets = asset.split(System.getProperty("line.separator"));
            if(assets==null||assets.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one input"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th(type.substring(0,1).toUpperCase()+type.substring(1).replaceAll("_"," ")),
                                    th("Probability"),
                                    th("Technology")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(assets)
                                    .filter(assignee->!(assignee==null||assignee.isEmpty()))
                                    .map(item->{
                                        System.out.println("Item length: "+item.length());
                                        item = item.trim();
                                        System.out.println("Item length after trim: "+item.length());
                                        final String prettyItem = item;
                                        List<Pair<String,Double>> pairs = tagger.attributesFor(PortfolioList.asList(item,portfolioType),1);
                                        String val = "";
                                        double probability = 0.0;
                                        if(!pairs.isEmpty()) {
                                            Pair<String,Double> pair = pairs.get(0);
                                            val+=pair.getFirst();
                                            probability+=pair.getSecond();
                                        }
                                        return tr().with(
                                                td(prettyItem),
                                                td(String.valueOf(probability)),
                                                td(val)
                                        );
                                    }).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/etsi_standards", (req, res) -> {
            res.type("application/json");
            String etsiStr = req.queryParams("etsi_standard");
            if(etsiStr==null||etsiStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one ETSI Standard"));

            String[] standards = etsiStr.split(System.getProperty("line.separator"));
            if(standards==null||standards.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one ETSI Standard"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("ETSI Standard"),
                                    th("Assets")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(standards)
                                    .filter(standard->!(standard==null||standard.isEmpty()))
                                    .map(standard->tr().with(
                                            td(standard),
                                            td(String.join(" ",Database.selectPatentNumbersFromETSIStandard(GetEtsiPatentsList.cleanETSIString(standard)))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });




        post("/cpc_to_assets", (req, res) -> {
            res.type("application/json");
            String classCodeStr = req.queryParams("class_code");
            if(classCodeStr==null||classCodeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Class Code"));
            boolean includeSubclasses = extractBool(req, "includeSubclasses");

            String[] classCodes = classCodeStr.split(System.getProperty("line.separator"));
            if(classCodes==null||classCodes.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Class Code"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Class Code"),
                                    th("Assets")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(classCodes)
                                    .filter(code->!(code==null||code.isEmpty()))
                                    .map(code->tr().with(
                                            td(code),
                                            td(String.join(" ",(includeSubclasses?Database.selectPatentNumbersFromClassAndSubclassCodes(ClassCodeHandler.convertToLabelFormat(code)):Database.selectPatentNumbersFromExactClassCode(ClassCodeHandler.convertToLabelFormat(code))))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/title_from_assets", (req, res) -> {
            res.type("application/json");
            String patentStr = req.queryParams("patent");
            if(patentStr==null||patentStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));

            String[] patents = patentStr.split("\\s+");
            if(patents==null||patents.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Asset"),
                                    th("Title")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(patents)
                                    .filter(patent->!(patent==null||patent.isEmpty()))
                                    .map(patent->tr().with(
                                            td(patent),
                                            td(Database.getInventionTitleFor(patent)))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });


        post("/cpc_from_assets", (req, res) -> {
            res.type("application/json");
            String patentStr = req.queryParams("patent");
            if(patentStr==null||patentStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));
            boolean includeSubclasses = extractBool(req, "includeSubclasses");

            String[] patents = patentStr.split(System.getProperty("line.separator"));
            if(patents==null||patents.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));
            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Asset"),
                                    th("Classifications")
                            )
                    ),
                    tbody().with(
                            Arrays.stream(patents)
                                    .filter(patent->!(patent==null||patent.isEmpty()))
                                    .map(patent->tr().with(
                                            td(patent),
                                            td(String.join("; ",(includeSubclasses?Database.subClassificationsForPatent(patent.replaceAll("[^0-9]","")):Database.classificationsFor(patent.replaceAll("[^0-9]",""))).stream()
                                                    .map(cpc->ClassCodeHandler.convertToHumanFormat(cpc)).collect(Collectors.toList()))))
                                    ).collect(Collectors.toList())

                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

        post("/cpc_frequencies_from_assets", (req, res) -> {
            res.type("application/json");
            String patentStr = req.queryParams("patent");
            if(patentStr==null||patentStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));
            boolean includeSubclasses = extractBool(req, "includeSubclasses");

            String[] patents = patentStr.split("\\s+");
            if(patents==null||patents.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one Patent"));

            AtomicInteger cnt = new AtomicInteger(0);
            Map<String,Double> classScoreMap = new HashMap<>();
            Arrays.stream(patents).forEach(patent->{
                if(patent==null||patent.trim().isEmpty()) return;
                Collection<String> data = (includeSubclasses?Database.subClassificationsForPatent(patent.replaceAll("[^0-9]","")):Database.classificationsFor(patent.replaceAll("[^0-9]","")));
                if(data.isEmpty()) return;
                data.stream()
                        .forEach(cpc->{
                            if(classScoreMap.containsKey(cpc)) {
                                classScoreMap.put(cpc,classScoreMap.get(cpc)+1.0);
                            } else {
                                classScoreMap.put(cpc,1.0);
                            }
                        });
                cnt.getAndIncrement();
            });

            Map<String,Double> globalFrequencyMap = new HashMap<>();
            Map<String,Double> ratioMap = new HashMap<>();
            // standardize numbers
            Set<String> keys = new HashSet<>(classScoreMap.keySet());
            keys.forEach(key->{
                classScoreMap.put(key,classScoreMap.get(key)/cnt.get());
                globalFrequencyMap.put(key,new Double(Database.selectPatentNumbersFromExactClassCode(key).size())/Database.numPatentsWithCpcClassifications());
                ratioMap.put(key,classScoreMap.get(key)*Math.log(1.0+globalFrequencyMap.get(key)));
            });

            Tag table = table().with(
                    thead().with(
                            tr().with(
                                    th("Class Code"),
                                    th("Frequency in Portfolio"),
                                    th("Global Frequency"),
                                    th("Frequency Score (f_p*log(1+f_g))")
                            )
                    ),
                    tbody().with(
                            ratioMap.entrySet().stream()
                                    .sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                                    .map(e->tr().with(
                                            td(ClassCodeHandler.convertToHumanFormat(e.getKey())),
                                            td(classScoreMap.get(e.getKey()).toString()),
                                            td(globalFrequencyMap.get(e.getKey()).toString()),
                                            td(e.getValue().toString())
                                    )).collect(Collectors.toList())
                    )
            );
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });

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
                int limitPerInput = extractInt(req, "limitPerInput", 10);
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

                PortfolioList portfolioList = runPatentFinderModel(firstFinder, secondFinders, limitPerInput, limit, threshold, labelsToExclude, badAssignees, portfolioType);
                if (assigneePortfolioLimit > 0) portfolioList.filterPortfolioSize(assigneePortfolioLimit);

                modelMap.forEach((key,model)->{
                    if (attributes.contains(key) && model != null) {
                        evaluateModel(model,portfolioList.getPortfolio(),key,portfolioType);
                    }
                });

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
                portfolioList.init(comparator,limit);

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

    static PortfolioList runPatentFinderModel(AbstractSimilarityModel firstFinder, List<? extends AbstractSimilarityModel> secondFinders, int limitPerInput, int totalLimit, double threshold, Collection<String> badLabels, Collection<String> badAssignees, PortfolioList.Type portfolioType) {
        List<PortfolioList> portfolioLists = new ArrayList<>();
        try {
            List<PortfolioList> similar = firstFinder.similarFromCandidateSets(secondFinders, threshold, limitPerInput, badLabels, portfolioType);
            portfolioLists.addAll(similar);
        } catch(Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        System.out.println("SIMILAR PATENTS FOUND!!!");
        return mergePatentLists(portfolioLists,badAssignees, portfolioType,totalLimit);
    }

    private static PortfolioList mergePatentLists(List<PortfolioList> portfolioLists, Collection<String> assigneeFilter, PortfolioList.Type portfolioType, int totalLimit) {
        try {
            Map<String, Item> map = new HashMap<>();
            portfolioLists.forEach(portfolioList -> {
                portfolioList.getPortfolio().forEach(item -> {
                    try {
                        if (item.getName() == null || item.getName().length() == 0) return;
                        if (map.containsKey(item.getName())) {
                            map.get(item.getName()).appendTags(item.getTags());
                            map.get(item.getName()).setSimilarity(Math.max(item.getSimilarity(), map.get(item.getName()).getSimilarity()));
                        } else {
                            map.put(item.getName(), item);
                        }
                    } catch(Exception e) {
                        throw new RuntimeException("Error on item: "+item.getName());
                    }
                });
            });
            try {
                List<Item> merged = map.values().stream()
                        .filter(p -> !(((p instanceof AbstractAssignee) && assigneeFilter.contains(p.getName())) || ((p instanceof AbstractPatent) && assigneeFilter.contains(((AbstractPatent) p).getAssignee()))))
                        .sorted((o, o2) -> Double.compare(o2.getSimilarity(), o.getSimilarity()))
                        .limit(totalLimit)
                        .collect(Collectors.toList());
                return new PortfolioList(merged,portfolioType);

            } catch(Exception e) {
                throw new RuntimeException("Error merging values in final stream.");
            }
        } catch(Exception e) {
            throw new RuntimeException("Error merging patent lists: "+e.getMessage());
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
                                                        ),hr(),expandableDiv("Advanced Options",
                                                                h3("Advanced Options"),
                                                                label("Patent Limit"),br(),input().withType("text").withName("limit"), br(),
                                                                label("Patent Limit Per Input"),br(),input().withType("text").withName("limitPerInput"), br(),
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

    private static Tag patentToolboxForm() {
        return div().with(
                formScript(ASSIGNEE_ASSET_COUNT_FORM_ID, "/assignee_asset_count", "Search", true),
                formScript(ASSIGNEE_ASSETS_FORM_ID, "/assignee_assets", "Search", true),
                formScript(PATENTS_FROM_ETSI_FORM_ID, "/etsi_standards", "Search", true),
                formScript(CPC_TO_ASSETS_FORM_ID, "/cpc_to_assets", "Search", true),
                formScript(CPC_FROM_ASSETS_FORM_ID, "/cpc_from_assets", "Search", true),
                formScript(TITLE_FROM_ASSETS_FORM_ID, "/title_from_assets", "Search", true),
                formScript(CPC_FREQUENCY_FROM_ASSETS_FORM_ID, "/cpc_frequencies_from_assets", "Search",true),
                formScript(TECH_PREDICTION_FROM_ASSETS_FORM_ID,"/tech_predictions", "Search", true),
                formScript(TECH_PREDICTION_FROM_ASSIGNEES_FORM_ID,"/tech_predictions", "Search", true),
                formScript(TECH_PREDICTION_FROM_CPCS_FORM_ID,"/tech_predictions", "Search", true),
                p("(Warning: Data only available from 2007 and onwards)"),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Asset Count for Assignees (Approximation Only)"),
                                                h4("Please place each assignee on a separate line"),
                                                form().withId(ASSIGNEE_ASSET_COUNT_FORM_ID).with(
                                                        label("Assignees"),br(),textarea().withName("assignee"), br(),
                                                        button("Search").withId(ASSIGNEE_ASSET_COUNT_FORM_ID+"-button").withType("submit")
                                                )
                                        ),
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Current Assets for Assignees (Approximation Only)"),
                                                h4("Please place each assignee on a separate line"),
                                                form().withId(ASSIGNEE_ASSETS_FORM_ID).with(
                                                        label("Assignees"),br(),textarea().withName("assignee"), br(),
                                                        label("Use Patents View API?"),br(),input().withType("checkbox").withName("use_patents_view"), br(),
                                                        button("Search").withId(ASSIGNEE_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        ),
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Current Assets for ETSI Standard (Approximation Only)"),
                                                h4("Please place each ETSI Standard on a separate line"),
                                                form().withId(PATENTS_FROM_ETSI_FORM_ID).with(
                                                        label("ETSI Standards"),br(),textarea().withName("etsi_standard"), br(),
                                                        button("Search").withId(PATENTS_FROM_ETSI_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                ),tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get patents from CPC (Approximation Only)"),
                                                h4("Please place each CPC code on a separate line"),
                                                form().withId(CPC_TO_ASSETS_FORM_ID).with(
                                                        label("CPC Class Codes"),br(),textarea().withName("class_code"), br(),
                                                        label("Include CPC Subclasses?"),br(),input().withType("checkbox").withName("includeSubclasses"),br(),
                                                        button("Search").withId(CPC_TO_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        ),
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get CPC for patents (Approximation Only)"),
                                                h4("Please place each patent on a separate line"),
                                                form().withId(CPC_FROM_ASSETS_FORM_ID).with(
                                                        label("Patents"),br(),textarea().withName("patent"), br(),
                                                        label("Include CPC Subclasses?"),br(),input().withType("checkbox").withName("includeSubclasses"),br(),
                                                        button("Search").withId(CPC_FROM_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        ),td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get CPC frequencies for patents (Approximation Only)"),
                                                h4("Please place each patent on a separate line"),
                                                form().withId(CPC_FREQUENCY_FROM_ASSETS_FORM_ID).with(
                                                        label("Patents"),br(),textarea().withName("patent"), br(),
                                                        label("Include CPC Subclasses?"),br(),input().withType("checkbox").withName("includeSubclasses"),br(),
                                                        button("Search").withId(CPC_FREQUENCY_FROM_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                ),tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Technology from Patents (Approximation Only)"),
                                                h4("Please place each Patent on a separate line"),
                                                form().withId(TECH_PREDICTION_FROM_ASSETS_FORM_ID).with(
                                                        input().withType("hidden").withName("report_type").withValue("patents"),
                                                        label("Patents"),br(),textarea().withName("item"), br(),
                                                        button("Search").withId(TECH_PREDICTION_FROM_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        ),
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Technology from Assignees (Approximation Only)"),
                                                h4("Please place each Assignee on a separate line"),
                                                form().withId(TECH_PREDICTION_FROM_ASSIGNEES_FORM_ID).with(
                                                        input().withType("hidden").withName("report_type").withValue("assignees"),
                                                        label("Assignees"),br(),textarea().withName("item"), br(),
                                                        button("Search").withId(TECH_PREDICTION_FROM_ASSIGNEES_FORM_ID+"-button").withType("submit")
                                                )
                                        ),td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Technology from CPC Codes (Approximation Only)"),
                                                h4("Please place each CPC Code on a separate line"),
                                                form().withId(TECH_PREDICTION_FROM_CPCS_FORM_ID).with(
                                                        input().withType("hidden").withName("report_type").withValue("class_codes"),
                                                        label("CPC Codes"),br(),textarea().withName("item"), br(),
                                                        button("Search").withId(TECH_PREDICTION_FROM_CPCS_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                ),tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Invention Title for patents"),
                                                h4("Please place each patent on a separate line"),
                                                form().withId(TITLE_FROM_ASSETS_FORM_ID).with(
                                                        label("Patents"),br(),textarea().withName("patent"), br(),
                                                        button("Search").withId(TITLE_FROM_ASSETS_FORM_ID+"-button").withType("submit")
                                                )
                                        )

                                )
                        )
                ),
                br(),
                br()
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
        System.out.println("Finished starting server.");
        LeadDevelopmentUI.setupServer();
        System.out.println("Starting Company Profile UI Server...");
        CompanyPortfolioProfileUI.setupServer();
        System.out.println("Finished starting server.");
        System.out.println("Starting Tech Tagger UI Server...");
        TechTaggerUI.setupServer();
        GatherClassificationServer.StartServer();
        System.out.println("Finished starting server.");
    }
}
