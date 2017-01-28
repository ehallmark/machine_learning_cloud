package server;

import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import dl4j_neural_nets.tools.MyPreprocessor;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import j2html.tags.Tag;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import seeding.Constants;
import seeding.Database;
import server.tools.AbstractAssignee;
import server.tools.AbstractPatent;
import server.tools.SimpleAjaxMessage;
import server.tools.excel.ExcelHandler;
import server.tools.excel.ExcelWritable;
import spark.Request;
import spark.Response;
import spark.Session;
import tools.ClassCodeHandler;
import tools.PortfolioList;
import value_estimation.ClassificationEvaluator;
import value_estimation.Evaluator;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
    public static SimilarPatentFinder globalFinder;
    public static SimilarPatentFinder assigneeFinder;
    public static SimilarPatentFinder classCodeFinder;
    private static final String SELECT_BETWEEN_CANDIDATES_FORM_ID = "select-between-candidates-form";
    private static final String ASSIGNEE_ASSET_COUNT_FORM_ID = "select-assignee-asset-count-form";
    protected static ParagraphVectors paragraphVectors;
    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    private static Evaluator valueModel;
    private static Map<String,String> humanParamMap = ExcelWritable.getHumanAttrToJavaAttrMap();
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }



    protected static void loadLookupTable() throws IOException {
        if(paragraphVectors!=null)return;
        try {
            paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
        } catch(Exception e) {
            paragraphVectors = ParagraphVectorModel.loadAllClaimsModel();
            e.printStackTrace();
            System.out.println("DEFAULTING TO OLDER MODEL");
        }
    }

    private static void loadBaseFinder() {
        try {
            globalFinder =  new SimilarPatentFinder(Database.getValuablePatents(),"** ALL PATENTS **",paragraphVectors.lookupTable());
            assigneeFinder = new SimilarPatentFinder(Database.getAssignees(),"** ALL ASSIGNEES **",paragraphVectors.lookupTable());
            classCodeFinder = new SimilarPatentFinder(Database.getClassCodes(),"** ALL CLASS CODES **",paragraphVectors.lookupTable());
            // value model
           // valueModel=new ClassificationEvaluator(paragraphVectors.getLookupTable());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    private static String getAndRemoveMessage(Session session) {
        String message = session.attribute("message");
        if(message!=null)session.removeAttribute("message");
        return message;
    }

    private static Tag homePage() {
        return div().with(
                h3().with(
                        a("Portfolio Comparison").withHref("/candidate_set_models")
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

            String[] assignees = assigneeStr.split("\\n");
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

        /*post("/knowledge_base_predictions", (req, res) ->{
            res.type("application/json");

            // create html
            AbstractClassCode table;
            if(searchType.equals("patents")) {
                table = table().with(
                        thead().with(
                                tr().with(
                                        th("Pat No."),
                                        th("Similarity"),
                                        th("Assignee"),
                                        th("Invention Title")
                                )
                        ),tbody().with(
                                patentList.getPatents().stream().map(item->tr().with(
                                        td(item.getName()),
                                        td(String.valueOf(item.getSimilarity())),
                                        td(item.getFullAssignee()),
                                        td(item.getInventionTitle())
                                )).collect(Collectors.toList())
                        )
                );
            } else if(searchType.equals("assignees")) {
                table = table().with(
                        thead().with(
                                tr().with(
                                        th("Assignee"),
                                        th("Similarity"),
                                        th("Approx. Num Assets")
                                )
                        ),tbody().with(
                                patentList.getPatents().stream().map(item->tr().with(
                                        td(item.getName()),
                                        td(String.valueOf(item.getSimilarity())),
                                        td(String.valueOf(Database.getAssetCountFor(item.getName())))
                                )).collect(Collectors.toList())
                        )
                );
            } else {
                table = table().with(
                        thead().with(
                                tr().with(
                                        th("Code"),
                                        th("Similarity"),
                                        th("Title")
                                )
                        ),tbody().with(
                                patentList.getPatents().stream().map(item->tr().with(
                                        td(ClassCodeHandler.convertToHumanFormat(item.getName())),
                                        td(String.valueOf(item.getSimilarity())),
                                        td(Database.getClassTitleFromClassCode(ClassCodeHandler.convertToHumanFormat(item.getName())))
                                )).collect(Collectors.toList())
                        )
                );
            }
            return new Gson().toJson(new SimpleAjaxMessage(table.render()));
        });*/

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

        // Host my own image asset!
        get("/images/most_recent_tree.gif", (request, response) -> {
            response.type("image/gif");

            String pathToImage = "images/most_recent_tree.gif";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "gif", out);
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
                PortfolioList.Type portfolioType = searchType.equals("patents") ? PortfolioList.Type.patents : searchType.equals("assignees") ? PortfolioList.Type.assignees : PortfolioList.Type.class_codes;
                if(req.queryParamsValues("dataAttributes")==null) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Please choose some data fields to report.");
                    return null;
                }
                List<String> attributes = Arrays.stream(req.queryParamsValues("dataAttributes")).collect(Collectors.toList());
                boolean gatherValue = extractBool(req, "gather_value");
                boolean allowResultsFromOtherCandidateSet = extractBool(req, "allowResultsFromOtherCandidateSet");
                boolean searchEntireDatabase = extractBool(req, "search_all");
                int assigneePortfolioLimit = extractInt(req, "portfolio_limit", -1);

                SimilarPatentFinder firstFinder;
                Set<String> labelsToExclude = new HashSet<>();
                if (!allowResultsFromOtherCandidateSet) {
                    classCodesToSearchFor.forEach(code->{
                        labelsToExclude.addAll(Database.subClassificationsForClass(code));
                    });
                    assigneesToSearchFor.forEach(assignee->{
                        labelsToExclude.addAll(Database.possibleNamesForAssignee(assignee));
                    });
                    labelsToExclude.addAll(patentsToSearchFor);
                }

                // get first finder
                if (searchEntireDatabase) {
                    if (searchType.equals("patents")) {
                        firstFinder = globalFinder;
                    } else if (searchType.equals("assignees")) {
                        firstFinder = assigneeFinder;
                    } else if (searchType.equals("class_codes")) {
                        firstFinder = classCodeFinder;
                    } else {
                        res.redirect("/candidate_set_models");
                        req.session().attribute("message", "Please enter a valid search type.");
                        return null;
                    }
                } else {
                    // pre data
                    Collection<String> classCodesToSearchIn = new HashSet<>();
                    preProcess(extractString(req, "custom_class_code_list", "").toUpperCase(), "\n", null).stream()
                            .map(classCode -> ClassCodeHandler.convertToLabelFormat(classCode)).forEach(cpc -> classCodesToSearchIn.addAll(Database.subClassificationsForClass(cpc)));
                    Collection<String> patentsToSearchIn = new HashSet<>(preProcess(extractString(req, "custom_patent_list", ""), "\\s+", "[^0-9]"));
                    Collection<String> assigneesToSearchIn = new HashSet<>();
                    List<String> customAssigneeList = preProcess(extractString(req, "custom_assignee_list", "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
                    customAssigneeList.forEach(assignee -> {
                        if (assigneePortfolioLimit > 0 && Database.getAssetCountFor(assignee) > assigneePortfolioLimit)
                            return;
                        assigneesToSearchIn.addAll(Database.possibleNamesForAssignee(assignee));
                    });

                    // dependent on searchType
                    if (searchType.equals("patents")) {
                        customAssigneeList.forEach(assignee -> patentsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
                        classCodesToSearchIn.forEach(cpc -> patentsToSearchIn.addAll(Database.selectPatentNumbersFromClassCode(cpc)));
                        firstFinder = new SimilarPatentFinder(patentsToSearchIn, null, paragraphVectors.getLookupTable());
                    } else if (searchType.equals("assignees")) {
                        classCodesToSearchIn.forEach(cpc -> patentsToSearchIn.addAll(Database.selectPatentNumbersFromClassCode(cpc)));
                        patentsToSearchIn.forEach(patent -> Database.assigneesFor(patent).forEach(assignee -> assigneesToSearchIn.addAll(Database.possibleNamesForAssignee(assignee))));
                        firstFinder = new SimilarPatentFinder(assigneesToSearchIn, null, paragraphVectors.getLookupTable());
                    } else if (searchType.equals("class_codes")) {
                        customAssigneeList.forEach(assignee -> patentsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
                        patentsToSearchIn.forEach(patent -> classCodesToSearchIn.addAll(Database.classificationsFor(patent)));
                        firstFinder = new SimilarPatentFinder(classCodesToSearchIn, null, paragraphVectors.getLookupTable());
                    } else {
                        res.redirect("/candidate_set_models");
                        req.session().attribute("message", "Please enter a valid search type.");
                        return null;
                    }
                }

                if (firstFinder == null || firstFinder.getPatentList().size() == 0) {
                    res.redirect("/candidate_set_models");
                    req.session().attribute("message", "Unable to find any results to search in.");
                    return null;
                }

                List<SimilarPatentFinder> secondFinders = new ArrayList<>();
                Set<String> stuffToSearchFor = new HashSet<>();
                stuffToSearchFor.addAll(patentsToSearchFor);
                stuffToSearchFor.addAll(assigneesToSearchFor);
                stuffToSearchFor.addAll(classCodesToSearchFor);

                stuffToSearchFor.forEach(patent -> secondFinders.add(new SimilarPatentFinder(Arrays.asList(patent), patent, paragraphVectors.getLookupTable())));
                // handle words slightly differently
                if (!wordsToSearchFor.isEmpty())
                    secondFinders.add(new SimilarPatentFinder(wordsToSearchFor, "Custom Text", paragraphVectors.getLookupTable()));

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

                PortfolioList portfolioList = runPatentFinderModel(title, firstFinder, secondFinders, limit, threshold, labelsToExclude, badAssignees, portfolioType);
                if (assigneePortfolioLimit > 0) portfolioList.filterPortfolioSize(assigneePortfolioLimit);

                if (gatherValue && valueModel != null) {
                    for (ExcelWritable item : portfolioList.getPortfolio()) {
                        try {
                            Double score = valueModel.evaluate(item.getName());
                            item.setAttribute("gatherValue", score, ExcelHandler.getDefaultFormat());
                            System.out.println("Value for patent: " + score);
                        } catch (Exception e) {
                            System.out.println("Unable to find value");
                            item.setAttribute("gatherValue", 0d, ExcelHandler.getDefaultFormat());
                        }
                    }
                }

                {
                    String keywordsToRequire = extractString(req, "required_keywords", null);
                    if (keywordsToRequire != null) {
                        String[] keywords = keywordsToRequire.toLowerCase().replaceAll("[^a-z \\n%]", " ").split("\\n");
                        if (keywords.length > 0) {
                            Set<String> patents = Database.patentsWithAllKeywords(portfolioList.getPortfolioAsStrings(), keywords);
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
                        String[] keywords = keywordsToAvoid.toLowerCase().replaceAll("[^a-z \\n%]", " ").split("\\n");
                        if (keywords.length > 0) {
                            Set<String> patents = Database.patentsWithKeywords(portfolioList.getPortfolioAsStrings(), keywords);
                            portfolioList.setPortfolio(portfolioList.getPortfolio().stream()
                                    .filter(patent -> !patents.contains(patent.getName()))
                                    .collect(Collectors.toList())
                            );
                        }
                    }
                }
                portfolioList.init();

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
                    ExcelHandler.writeDefaultSpreadSheetToRaw(raw, highlightAssignees, title, clientName, EMData, SAMData, attributes, portfolioList);
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

    private static PortfolioList runPatentFinderModel(String name, SimilarPatentFinder firstFinder, List<SimilarPatentFinder> secondFinders, int limit, double threshold, Collection<String> badLabels, Collection<String> badAssignees, PortfolioList.Type portfolioType) {
    List<PortfolioList> portfolioLists = new ArrayList<>();
        try {
            List<PortfolioList> similar = firstFinder.similarFromCandidateSets(secondFinders, threshold, limit, badLabels, portfolioType);
            portfolioLists.addAll(similar);
        } catch(Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        System.out.println("SIMILAR PATENTS FOUND!!!");
        return mergePatentLists(portfolioLists,badAssignees, name, portfolioType);
    }

    private static PortfolioList mergePatentLists(List<PortfolioList> portfolioLists, Collection<String> assigneeFilter, String name, PortfolioList.Type portfolioType) {
        try {
            Map<String, ExcelWritable> map = new HashMap<>();
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
                List<ExcelWritable> merged = map.values().stream().filter(p -> !(((p instanceof AbstractAssignee) && assigneeFilter.contains(p.getName())) || ((p instanceof AbstractPatent) && assigneeFilter.contains(((AbstractPatent) p).getAssignee())))).sorted((o, o2) -> Double.compare(o2.getSimilarity(), o.getSimilarity())).collect(Collectors.toList());
                return new PortfolioList(merged,name,name,portfolioType);

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

    private static Tag templateWrapper(Response res, Tag form, String message) {
        res.type("text/html");
        if(message==null)message="";
        return html().with(
                head().with(
                        script().attr("src","https://ajax.googleapis.com/ajax/libs/jquery/3.0.0/jquery.min.js"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().attr("OnKeyPress","return disableKeyPress(event);").with(
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

    private static Tag formScript(String formId, String url, String buttonText, boolean ajax) {
        return script().withText(ajax?
                ("$(document).ready(function() { "
                          + "$('#"+formId+"').submit(function(e) {"
                            + "$('#"+formId+"-button').attr('disabled',true).text('"+buttonText+"ing...');"
                            + "var url = '"+url+"'; "
                            + "$.ajax({"
                            + "  type: 'POST',"
                            + "  url: url,"
                            + "  data: $('#"+formId+"').serialize(),"
                            + "  success: function(data) { "
                            + "    $('#results').html(data.message); "
                            + "    $('#"+formId+"-button').attr('disabled',false).text('"+buttonText+"');"
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

    private static Tag expandableDiv(String label, Tag... innnerStuff) {
        String id = "div-"+label.hashCode();
        return div().with(label("Toggle "+label).attr("style","cursor: pointer; color: blue; text-decoration: underline;").attr("onclick","$('#"+id+"').toggle();"),
                div().withId(id).attr("style","display: none;").with(
                        innnerStuff
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
                                                        h3("Main options"),
                                                        h4("Search for results in"),
                                                        label("Entire Database"),input().withType("checkbox").withName("search_all"),br(),
                                                        h4("Or by"),
                                                        label("Custom Patent List (1 per line)"),br(),
                                                        textarea().withName("custom_patent_list"),br(),
                                                        label("Custom Assignee List (1 per line)"),br(),
                                                        textarea().withName("custom_assignee_list"),br(),
                                                        label("Custom CPC Class Code List (1 per line)"),br(),
                                                        label("Example: F05D 01/233"),br(),
                                                        textarea().withName("custom_class_code_list"),br(),
                                                        h4("To find"),select().withName("search_type").with(
                                                                option().withValue("patents").attr("selected","true").withText("Patents"),
                                                                option().withValue("assignees").withText("Assignees"),
                                                                option().withValue("class_codes").withText("CPC Class Codes")
                                                        ),br(),h4("With relevance to"),
                                                        label("Patents (1 per line)"),br(),textarea().withName("patents"),
                                                        br(),
                                                        label("Assignees (1 per line)"),br(),textarea().withName("assignees"),
                                                        br(),
                                                        label("CPC Class Codes (1 per line)"),br(),
                                                        label("Example: F05D 01/233"),br(),
                                                        textarea().withName("class_codes"),
                                                        br(),
                                                        label("Arbitrary Text"),br(),
                                                        textarea().withName("words"),
                                                        hr(),
                                                        expandableDiv("Data Fields",h3("Select Data Fields to capture"),
                                                                select().attr("multiple","true").withName("dataAttributes").with(
                                                                        humanParamMap.entrySet().stream().map(e-> {
                                                                            return option().withText(e.getKey()).withValue(e.getValue());
                                                                        }).collect(Collectors.toList())
                                                                )
                                                        ),expandableDiv("Advanced Options",
                                                                h3("Advanced Options"),
                                                                label("Patent Limit"),br(),input().withType("text").withName("limit"), br(),
                                                                label("Relevance Threshold"),br(),input().withType("text").withName("threshold"),br(),
                                                                label("Portfolio Size Limit"),br(),input().withType("text").withName("portfolio_limit"), br(),
                                                                label("Allow Search Documents in Results?"),br(),input().withType("checkbox").withName("allowResultsFromOtherCandidateSet"),br(),
                                                                label("Gather Value Model (Special Model)"),br(),input().withType("checkbox").withName("gather_value"),br(),
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
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Get Asset Count for Assignees (Estimation Only)"),
                                                h4("Please place each assignee on a separate line"),
                                                form().withId(ASSIGNEE_ASSET_COUNT_FORM_ID).with(
                                                        label("Assignee"),br(),textarea().withName("assignee"), br(),
                                                        button("Search").withId(ASSIGNEE_ASSET_COUNT_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                )
                        )
                ),
                br(),
                br()
        );
    }

    private static String extractString(Request req, String param, String defaultVal) {
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

    private static int extractInt(Request req, String param, int defaultVal) {
        try {
            return Integer.valueOf(req.queryParams(param));
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return defaultVal;
        }
    }

    private static boolean extractBool(Request req, String param) {
        try {
            return (req.queryParams(param)==null||!req.queryParams(param).startsWith("on")) ? false : true;
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        //Database.setupSeedConn();
        System.out.println("Starting to load lookup table...");
        loadLookupTable();
        System.out.println("Finished loading lookup table...");
        System.out.println("Starting to load base finder...");
        loadBaseFinder();
        System.out.println("Finished loading base finder...");
        System.out.println("Starting server...");
        server();
        System.out.println("Finished starting server...");
    }
}
