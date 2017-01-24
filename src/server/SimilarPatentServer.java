package server;

import analysis.Patent;
import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import dl4j_neural_nets.classifiers.GatherTransactionProbabilityModel;
import dl4j_neural_nets.tools.MyPreprocessor;
import dl4j_neural_nets.tools.PhrasePreprocessor;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import server.tools.AbstractPatent;
import server.tools.RespondWithJXL;
import server.tools.SimpleAjaxMessage;
import spark.Request;
import spark.Response;
import spark.Session;
import tools.Emailer;
import tools.PatentList;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    public static SimilarPatentFinder globalFinder;
    private static int DEFAULT_LIMIT = 3;
    private static final String NEW_CANDIDATE_FORM_ID = "new-candidate-form";
    private static final String KNOWLEDGE_BASE_FORM_ID = "knowledge-base-form";
    private static final String SELECT_BETWEEN_CANDIDATES_FORM_ID = "select-between-candidates-form";
    private static final String ASSIGNEE_ASSET_COUNT_FORM_ID = "select-assignee-asset-count-form";
    private static Map<Integer, Pair<Boolean, String>> candidateSetMap;
    private static Map<Integer, List<Integer>> groupedCandidateSetMap;
    protected static ParagraphVectors paragraphVectors;
    private static String GATHER_RATINGS_ID;
    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    private static MultiLayerNetwork transactionProbabilityModel = GatherTransactionProbabilityModel.load();
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }



    protected static void loadLookupTable() throws IOException {
        if(paragraphVectors!=null)return;
        //lookupTable = org.deeplearning4j.models.embeddings.loader.WordVectorSerializer.readParagraphVectorsFromText(new File("wordvectorexample2.txt")).getLookupTable();
        //lookupTable = ParagraphVectorModel.loadClaimModel().getLookupTable();
        try {
            paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("DEFAULTING TO OLDER MODEL");
            paragraphVectors = ParagraphVectorModel.loadAllClaimsModel();
        }
    }

    private static void loadBaseFinder() {
        try {
            globalFinder = new SimilarPatentFinder(paragraphVectors.lookupTable());
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
                        a("Knowledge Base").withHref("/knowledge_base")
                ), br(),
                h3().with(
                        a("Portfolio Comparison").withHref("/candidate_set_models")
                ), br(),
                h3().with(
                        a("Additional Patent Tools").withHref("/patent_toolbox")
                ),br()
        );
    }

    private static INDArray avgVector(Collection<String> patents) {
        try {
            return SimilarPatentFinder.computeAvg(patents
                    .stream().map(patent->new Patent(patent,paragraphVectors.getLookupTable().vector(patent)))
                    .filter(patent->patent.getVector()!=null)
                    .collect(Collectors.toList()), null);
        } catch(Exception e) {
            return null;
        }
    }

    public static void server() {
        port(4568);
        get("/", (req, res) -> templateWrapper(res, div().with(homePage(),hr()), getAndRemoveMessage(req.session())));

        get("/patent_toolbox", (req, res) -> templateWrapper(res, div().with(patentToolboxForm(), hr()), getAndRemoveMessage(req.session())));

        get("/candidate_set_models", (req, res) -> templateWrapper(res, div().with(candidateSetModelsForm(), hr()), getAndRemoveMessage(req.session())));

        get("/knowledge_base", (req, res) -> templateWrapper(res, div().with(knowledgeBaseForm(),hr()), getAndRemoveMessage(req.session())));

        get("/new", (req, res) -> templateWrapper(res, createNewCandidateSetForm(), getAndRemoveMessage(req.session())));

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

        post("/create_group", (req, res) ->{
            if(req.queryParams("group_prefix")==null || req.queryParams("group_prefix").trim().length()==0) {
                req.session().attribute("message", "Invalid form parameters.");
                res.redirect("/new");
            } else {
                try {
                    String name = req.queryParams("group_prefix");
                    Database.createCandidateGroup(name);
                    req.session().attribute("message", "Candidate group created.");
                    res.redirect("/");

                } catch(SQLException sql) {
                    sql.printStackTrace();
                    req.session().attribute("message", "Database Error");
                    res.redirect("/new");

                }
            }
            return null;
        });

        post("/knowledge_base_predictions", (req, res) ->{
            res.type("application/json");
            StringJoiner sj = new StringJoiner("<br />");
            List<String> patents = preProcess(extractString(req,"patents",null),"\\s+","[^0-9]");
            List<String> assignees = preProcess(extractString(req,"assignees","").toUpperCase(),"\n","[^a-zA-Z0-9 ]");
            List<String> classCodes = preProcess(extractString(req,"class_codes","").toUpperCase(),"\n","[^a-zA-Z0-9 /]");
            String searchType = extractString(req,"search_type","patents");
            int limit = extractInt(req,"limit",10);
            sj.add("Patents: "+String.join("; ",patents));
            sj.add("Class codes: "+String.join("; ",classCodes));
            sj.add("Assignees: "+String.join("; ", assignees));
            sj.add("Search Type: "+searchType);
            sj.add("With limit: "+limit);

            Collection<String> labelsToSearch;
            if(searchType.equals("patents")) {
                labelsToSearch=Database.getValuablePatents();
            } else if(searchType.equals("assignees")) {
                labelsToSearch=Database.getAssignees();
            } else {
                return new Gson().toJson(new SimpleAjaxMessage("Please enter a valid search type."));
            }

            // get representative vector
            int numInputs = 0;

            INDArray patentVector = avgVector(patents);
            INDArray classVector = avgVector(classCodes);
            INDArray assigneeVector = avgVector(assignees);

            List<INDArray> representativeVectors = new ArrayList<>(3);
            if(patentVector!=null) {
                representativeVectors.add(patentVector);
            }

            if(classVector!=null) {
                representativeVectors.add(classVector);
            }

            if(assigneeVector!=null) {
                representativeVectors.add(assigneeVector);
            }

            if(representativeVectors.isEmpty()) {
                return new Gson().toJson(new SimpleAjaxMessage("Unable to find search terms."));
            }
            INDArray representativeVector;
            if(representativeVectors.size()>1) {
                representativeVector = Nd4j.vstack(representativeVectors).mean(0);
            } else {
                representativeVector = representativeVectors.get(0);
            }

            // search through labels
            paragraphVectors.sim

            // create html
            Tag table;
            return new Gson().toJson(table.render());
        });

        post("/create", (req, res) -> {
            if(req.queryParams("name")==null || (req.queryParams("patents")==null && req.queryParams("assignee")==null)) {
                req.session().attribute("message", "Invalid form parameters.");
                res.redirect("/new");
            } else {
                try {
                    String name = req.queryParams("name");
                    int id = Database.createCandidateSetAndReturnId(name);
                    // try to get percentages from form
                    File file = new File(Constants.CANDIDATE_SET_FOLDER+id);
                    if(req.queryParams("assignee")!=null&&req.queryParams("assignee").trim().length()>0) {
                        new SimilarPatentFinder(Database.selectPatentNumbersFromAssignee(req.queryParams("assignee")),file,name,paragraphVectors.lookupTable());
                    } else if (req.queryParams("patents")!=null&&req.queryParams("patents").trim().length()>0) {
                        new SimilarPatentFinder(preProcess(req.queryParams("patents"),"\\s+","US"), file, name, paragraphVectors.lookupTable());
                    } else if (req.queryParams("article")!=null&&req.queryParams("article").trim().length()>0) {
                        new SimilarPatentFinder(tokenizerFactory.create(new PhrasePreprocessor().preProcess(req.queryParams("article"))).getTokens(), file, name, paragraphVectors.lookupTable());
                    } else {
                        req.session().attribute("message", "Patents and Assignee parameters were blank. Please choose one to fill out");
                        res.redirect("/new");
                        return null;
                    }
                    req.session().attribute("message", "Candidate set created.");
                    res.redirect("/candidate_set_models");

                } catch(SQLException sql) {
                    sql.printStackTrace();
                    req.session().attribute("message", "Database Error: "+sql.toString());
                    res.redirect("/new");

                }
            }
            return null;
        });

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
            //long startTime = System.currentTimeMillis();

            HttpServletResponse raw = res.raw();
            res.header("Content-Disposition", "attachment; filename=download.xls");
            res.type("application/force-download");

            if(req.queryParamsValues("name2")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a second candidate set."));

            if(req.queryParams("name1")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a first candidate set."));

            boolean gatherValue = extractBool(req, "gather_value");
            int TagIndex = extractInt(req, "tag_index", 0);
            int limit = extractLimit(req);

            List<String> otherIds = Arrays.asList(req.queryParamsValues("name2"));
            if(otherIds.isEmpty()&&!gatherValue) return new Gson().toJson(new SimpleAjaxMessage("Must choose at least one other candidate set"));

            Integer id1 = Integer.valueOf(req.queryParams("name1"));

            // get more detailed params
            String clientName = extractString(req,"client","");
            int tagLimit = extractTagLimit(req);
            Integer minPatentNum = extractMinPatentNumber(req);
            double threshold = extractThreshold(req);
            List<String> badAssignees = preProcess(req.queryParams("assigneeFilter"),"\n",null);
            Set<String> badAssets = new HashSet<>(preProcess(req.queryParams("assetFilter"),"\\s+","US"));
            List<String> highlightAssignees = preProcess(req.queryParams("assigneeHighlighter"),"\n",null).stream().map(str->str.toUpperCase()).collect(Collectors.toList());
            String title = extractString(req,"title","");
            boolean switchCandidateSets = extractBool(req, "switchCandidateSets");
            boolean allowResultsFromOtherCandidateSet = extractBool(req, "allowResultsFromOtherCandidateSet");

            if(id1 < 0 && globalFinder==null)
                return new Gson().toJson(new SimpleAjaxMessage("Unable to find first candidate set."));
            else {

                // get similar patent finders
                List<SimilarPatentFinder> firstFinders = new ArrayList<>();
                if (id1 >= 0) {
                    if(groupedCandidateSetMap.containsKey(id1)) {
                        for(Integer id : groupedCandidateSetMap.get(id1)) {
                            String assignee = candidateSetMap.get(id).getSecond();
                            assignee=assignee.replaceFirst(candidateSetMap.get(Integer.valueOf(id1)).getSecond(),"").trim();
                            firstFinders.add(new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), assignee,paragraphVectors.lookupTable()));
                        }
                    } else {
                        String name1 = candidateSetMap.get(id1).getSecond();
                        firstFinders.add(new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id1), name1,paragraphVectors.lookupTable()));
                    }
                } else firstFinders = Arrays.asList(globalFinder);

                boolean findDissimilar = false;
                Pair<List<SimilarPatentFinder>,List<SimilarPatentFinder>> firstAndSecondFinders = getFirstAndSecondFinders(firstFinders,otherIds,switchCandidateSets);
                PatentList patentList = runPatentFinderModel(title, firstAndSecondFinders.getFirst(), firstAndSecondFinders.getSecond(), limit, threshold, findDissimilar, minPatentNum, badAssets, badAssignees,allowResultsFromOtherCandidateSet);

                if (gatherValue && transactionProbabilityModel != null) {
                    for (AbstractPatent patent : patentList.getPatents()) {
                        try {
                            INDArray vec = transactionProbabilityModel.output(SimilarPatentFinder.getVectorFromDB(patent.getName(), paragraphVectors.lookupTable()), false);
                            double value = vec.getDouble(1);
                            patent.setGatherValue(value);
                            System.out.println("Value for patent: "+value);
                        } catch(Exception e) {
                            System.out.println("Unable to find value");
                            patent.setGatherValue(0d);
                        }
                    }
                    patentList.setPatents(patentList.getPatents());
                }

                {
                    String keywordsToRequire = extractString(req, "required_keywords", null);
                    if (keywordsToRequire != null) {
                        String[] keywords = keywordsToRequire.toLowerCase().replaceAll("[^a-z \\n%]", " ").split("\\n");
                        if(keywords.length>0) {
                            Set<String> patents = Database.patentsWithAllKeywords(patentList.getPatentStrings(), keywords);
                            patentList.setPatents(patentList.getPatents().stream()
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
                        if(keywords.length>0) {
                            Set<String> patents = Database.patentsWithKeywords(patentList.getPatentStrings(), keywords);
                            patentList.setPatents(patentList.getPatents().stream()
                                    .filter(patent -> !patents.contains(patent.getName()))
                                    .collect(Collectors.toList())
                            );
                        }
                    }
                }
                patentList.init(tagLimit,TagIndex);

                // contact info
                String EMLabel = extractContactInformation(req,"label1", Constants.DEFAULT_EM_LABEL);
                String EMName=extractContactInformation(req,"cname1", Constants.DEFAULT_EM_NAME);
                String EMTitle=extractContactInformation(req,"title1", Constants.DEFAULT_EM_TITLE);
                String EMPhone=extractContactInformation(req,"phone1", Constants.DEFAULT_EM_PHONE);
                String EMEmail=extractContactInformation(req,"email1", Constants.DEFAULT_EM_EMAIL);
                String SAMName=extractContactInformation(req,"cname2", Constants.DEFAULT_SAM_NAME);
                String SAMTitle=extractContactInformation(req,"title2", Constants.DEFAULT_SAM_TITLE);
                String SAMPhone=extractContactInformation(req,"phone2", Constants.DEFAULT_SAM_PHONE);
                String SAMLabel = extractContactInformation(req,"label2", Constants.DEFAULT_SAM_LABEL);
                String SAMEmail=extractContactInformation(req,"email2", Constants.DEFAULT_SAM_EMAIL);
                String[] EMData = new String[]{EMLabel,EMName,EMTitle,EMPhone,EMEmail};
                String[] SAMData = new String[]{SAMLabel,SAMName, SAMTitle, SAMPhone, SAMEmail};
                try {
                    RespondWithJXL.writeDefaultSpreadSheetToRaw(raw, patentList, highlightAssignees,clientName, EMData, SAMData, tagLimit, gatherValue, TagIndex);
                } catch (Exception e) {

                    e.printStackTrace();
                }
                return raw;

            }

        });

    }

    private static Pair<List<SimilarPatentFinder>,List<SimilarPatentFinder>> getFirstAndSecondFinders(List<SimilarPatentFinder> firstFinders, List<String> otherIds, boolean switchCandidateSets) throws Exception {
        List<SimilarPatentFinder> secondFinders = new ArrayList<>();
        for(String id : otherIds) {
            SimilarPatentFinder finder = null;
            if (Integer.valueOf(id) >= 0) {
                try {
                    if(groupedCandidateSetMap.containsKey(Integer.valueOf(id))) {
                        for(Integer groupedId : groupedCandidateSetMap.get(Integer.valueOf(id))) {
                            System.out.println("CANDIDATE LOADING: " + candidateSetMap.get(groupedId).getSecond());
                            String assignee = candidateSetMap.get(groupedId).getSecond();
                            assignee=assignee.replaceFirst(candidateSetMap.get(Integer.valueOf(id)).getSecond(),"").trim();
                            finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + groupedId), assignee,paragraphVectors.lookupTable());
                            if (finder != null && finder.getPatentList() != null && !finder.getPatentList().isEmpty()) {
                                secondFinders.add(finder);
                            }
                        }
                    } else {
                        System.out.println("CANDIDATE LOADING: " + candidateSetMap.get(Integer.valueOf(id)).getSecond());
                        String assignee = candidateSetMap.get(Integer.valueOf(id)).getSecond();
                        finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), assignee,paragraphVectors.lookupTable());
                        if (finder != null && finder.getPatentList() != null && !finder.getPatentList().isEmpty()) {
                            secondFinders.add(finder);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } else {
                secondFinders.add(globalFinder);
            }
        }
        // switch candidate sets if necessary
        if(switchCandidateSets) {
            List<SimilarPatentFinder> tmpList = firstFinders;
            firstFinders=secondFinders;
            secondFinders=tmpList;
        }
        return new Pair<>(firstFinders, secondFinders);
    }

    private static PatentList runPatentFinderModel(String name, List<SimilarPatentFinder> firstFinders, List<SimilarPatentFinder> secondFinders, int limit, double threshold, boolean findDissimilar, Integer minPatentNum,Set<String> badAssets, List<String> badAssignees, boolean allowResultsFromOtherCandidateSet) {
        List<PatentList> patentLists = new ArrayList<>();
        try {
            for(SimilarPatentFinder first : firstFinders) {
                try {
                    List<PatentList> similar = first.similarFromCandidateSets(secondFinders, threshold, limit, findDissimilar, minPatentNum, badAssets,allowResultsFromOtherCandidateSet);
                    patentLists.addAll(similar);
                } catch(Exception e) {
                    new Emailer("While calculating similar candidate set: "+e.getMessage());
                }
            }
        }
        catch(Exception e) {
            new Emailer("IN OUTER LOOP of runpatentfindermodel: "+e.toString());
        }
        System.out.println("SIMILAR PATENTS FOUND!!!");
        return mergePatentLists(patentLists,badAssignees, name);
    }

    private static PatentList mergePatentLists(List<PatentList> patentLists, List<String> assigneeFilter, String name) {
        try {
            Map<String, AbstractPatent> map = new HashMap<>();
            patentLists.forEach(patentList -> {
                patentList.getPatents().forEach(patent -> {
                    if (patent.getAssignee() == null || patent.getAssignee().length() == 0) return;
                    if (map.containsKey(patent.getName())) {
                        map.get(patent.getName()).appendTags(patent.getTags());
                        map.get(patent.getName()).setSimilarity(Math.max(patent.getSimilarity(), map.get(patent.getName()).getSimilarity()));
                    } else {
                        map.put(patent.getName(), patent);
                    }
                });
            });
            List<AbstractPatent> merged = map.values().stream().filter(p->!assigneeFilter.stream().anyMatch(assignee->p.getAssignee().toUpperCase().startsWith(assignee.toUpperCase()))).sorted((o, o2)->Double.compare(o2.getSimilarity(),o.getSimilarity())).collect(Collectors.toList());
            return new PatentList(merged,name,name);
        } catch(Exception e) {
            new Emailer("Error in merge patent lists: "+e.toString());
            throw new RuntimeException("Error merging patent lists");
        }
    }

    private static List<String> preProcess(String toSplit, String delim, String toReplace) {
        if(toSplit==null||toSplit.trim().length()==0) return new ArrayList<>();
        return Arrays.asList(toSplit.split(delim)).stream().filter(str->str!=null).map(str->toReplace!=null&&toReplace.length()>0?str.trim().replaceAll(toReplace,""):str.trim()).collect(Collectors.toList());
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


    private static void importCandidateSetFromDB() throws SQLException {
        Map<String,List<Integer>> groupedSetNameMap = new HashMap<>();
        ResultSet rs = Database.selectGroupedCandidateSets();
        while(rs.next()) {
            groupedSetNameMap.put(rs.getString(1),new ArrayList<>());
        }
        rs.close();
        ResultSet candidates = Database.selectAllCandidateSets();
        while(candidates.next()) {
            String name = candidates.getString(1);
            AtomicBoolean hidden = new AtomicBoolean(false);
            for(Map.Entry<String,List<Integer>> e : groupedSetNameMap.entrySet()) {
                if(name.startsWith(e.getKey())) {
                    hidden.set(true);
                    e.getValue().add(candidates.getInt(2));
                    break;
                }
            }
            candidateSetMap.put(candidates.getInt(2),new Pair<>(hidden.get(),name));
        }
        candidates.close();
        int size = Collections.max(candidateSetMap.keySet())+1;
        for(Map.Entry<String,List<Integer>> e : groupedSetNameMap.entrySet()) {
            if(e.getValue().size()>0) {
                candidateSetMap.put(size,new Pair<>(false, e.getKey()));
                groupedCandidateSetMap.put(size, e.getValue());
                if(e.getKey().startsWith("Gather Rating")) {
                    GATHER_RATINGS_ID=String.valueOf(size);
                }
                size++;
            }
        }
    }

    private static Tag selectCandidateSetDropdown(String label, String name, boolean multiple) {
        candidateSetMap = new HashMap<>();
        groupedCandidateSetMap = new HashMap<>();
        candidateSetMap.put(-1, new Pair<>(false,"**ALL**")); // adds the default candidate set
        try {
            importCandidateSetFromDB();
        } catch(SQLException sql ) {
            sql.printStackTrace();
            return label("ERROR:: Unable to load candidate set.");
        }
        return div().with(
                label(label),
                br(),
                (multiple ? (select().attr("multiple","true")) : (select())).withName(name).with(
                        candidateSetMap.entrySet().stream().sorted((o1,o2)->Integer.compare(o1.getKey(),o2.getKey())).map(entry->{if(entry.getValue().getFirst()) {return null; } if(entry.getKey()<0) return option().withText(reasonablySizedString(entry.getValue().getSecond())).attr("selected","true").withValue(entry.getKey().toString()); else return option().withText(reasonablySizedString(entry.getValue().getSecond())).withValue(entry.getKey().toString());}).filter(t->t!=null).collect(Collectors.toList())
                )
        );
    }

    private static Tag selectGatherTechnologyDropdown(String label, String name) {
        candidateSetMap = new HashMap<>();
        groupedCandidateSetMap = new HashMap<>();
        candidateSetMap.put(-1, new Pair<>(false,"**ALL**")); // adds the default candidate set
        try {
            importCandidateSetFromDB();
        } catch(SQLException sql ) {
            sql.printStackTrace();
            return label("ERROR:: Unable to load candidate set.");
        }
        return div().with(
                label(label),
                br(),
                select().attr("multiple","true").withName(name).with(
                        GatherClassificationServer.gatherFinders.stream()
                                .sorted((o1,o2)->o1.getName().compareTo(o2.getName()))
                                .map(finder->option().withText(reasonablySizedString(finder.getName())).withValue(String.valueOf(finder.getID())))
                                .filter(t->t!=null)
                                .collect(Collectors.toList())
                )
        );
    }

    private static Tag coverPageForm() {
        return div().with(h3("Contact Info (primarily for the cover page)"),
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

    private static Tag candidateSetModelsForm() {
        return div().with(
                formScript(SELECT_BETWEEN_CANDIDATES_FORM_ID, "/similar_candidate_sets", "Search", false),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                a("Create a new Portfolio").withHref("/new"),
                                                h2("Portfolio Comparison Tool"),
                                                form().withId(SELECT_BETWEEN_CANDIDATES_FORM_ID).with(
                                                        h3("Main options"),
                                                        selectCandidateSetDropdown("Portfolio 1","name1",false),
                                                        selectCandidateSetDropdown("Portfolio 2", "name2",true),
                                                        label("Search Type (eg. 'Focused Search')"),br(),input().withType("text").withName("title"), br(),
                                                        label("Patent Limit"),br(),input().withType("text").withName("limit"), br(),
                                                        label("Min Patent Number"),br(),input().withType("text").withName("min_patent"),br(),
                                                        label("Threshold"),br(),input().withType("text").withName("threshold"),br(),
                                                        label("Switch Portfolios"),br(),input().withType("checkbox").withName("switchCandidateSets"),br(),
                                                        hr(),
                                                        h3("Advanced Options"),
                                                        label("Allow results from Portfolio 2?"),br(),input().withType("checkbox").withName("allowResultsFromOtherCandidateSet"),br(),
                                                        label("Tag Limit"),br(),input().withType("text").withName("tag_limit"), br(),
                                                        label("Gather Value Model (Special Model)"),br(),input().withType("checkbox").withName("gather_value"),br(),
                                                        label("Asset Filter (space separated)"),br(),textarea().attr("selected","true").withName("assetFilter"),br(),
                                                        label("Assignee Filter (; separated)"),br(),textarea().withName("assigneeFilter"),br(),
                                                        label("Assignee Highlighter (; separated)"),br(),textarea().withName("assigneeHighlighter"),br(),
                                                        label("Require keywords"),br(),textarea().withName("required_keywords"),br(),
                                                        label("Avoid keywords"),br(),textarea().withName("avoided_keywords"),br(),hr(),
                                                        coverPageForm(),
                                                        button("Search").withId(SELECT_BETWEEN_CANDIDATES_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                )
                        )
                ),
                br(),
                br()
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

    private static Tag knowledgeBaseForm() {
        return div().with(
                formScript(KNOWLEDGE_BASE_FORM_ID, "/knowledge_base_predictions", "Search", true),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                  td().attr("style","width:33%; vertical-align: top;").with(
                                          h2("Knowledge Base"),
                                          form().withId(KNOWLEDGE_BASE_FORM_ID).with(
                                                  label("Patents (1 per line)"),br(),textarea().withName("patents"),
                                                  br(),
                                                  label("Assignees (1 per line)"),br(),textarea().withName("assignees"),
                                                  br(),
                                                  label("CPC Class Codes (1 per line)"),br(),
                                                  label("Example: F05D 01/233"),br(),
                                                  textarea().withName("class_codes"),
                                                  br(),
                                                  label("Search for: "),br(),select().withName("search_type").with(
                                                          option().withValue("patents").attr("selected","true").withText("Patents"),
                                                          option().withValue("assignees").withText("Assignees")
                                                  ),br(),
                                                  label("Limit"),br(),input().withType("text").withName("limit"), br(),
                                                  button("Search").withId(KNOWLEDGE_BASE_FORM_ID+"-button").withType("submit")
                                          )
                                  )
                                )
                        )
                ),
                br(),
                br()
        );
    }

    private static Tag createNewCandidateSetForm() {
        return form().withId(NEW_CANDIDATE_FORM_ID).withAction("/create").withMethod("post").with(
                p().withText("(May take awhile...)"),
                label("Name"),br(),
                input().withType("text").withName("name"),
                br(),
                label("Seed By Assignee"),br(),
                input().withType("text").withName("assignee"),
                br(),
                label("Or By Patent List (space separated)"), br(),
                textarea().withName("patents"), br(),
                label("Or By Article Text"), br(),
                textarea().withName("article"), br(),
                button("Create").withId(NEW_CANDIDATE_FORM_ID+"-button").withType("submit")
        );
    }

    private static int extractLimit(Request req) {
        try {
            return Integer.valueOf(req.queryParams("limit"));
        } catch(Exception e) {
            System.out.println("No limit parameter specified... using default");
            return 500;
        }
    }

    private static int extractTagLimit(Request req) {
        try {
            return Integer.valueOf(req.queryParams("tag_limit"));
        } catch(Exception e) {
            System.out.println("No tag_limit parameter specified... using default");
            return DEFAULT_LIMIT;
        }
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


    private static Integer extractMinPatentNumber(Request req) {
        try {
            return Integer.valueOf(req.queryParams("min_patent"));
        } catch(Exception e) {
            System.out.println("No focused_min_patent specified... using null");
            return null;
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
        Database.setupSeedConn();
        System.out.println("Starting to load lookup table...");
        loadLookupTable();
        System.out.println("Finished loading lookup table...");
        if(!Arrays.asList(args).contains("dontLoadBaseFinder")) {
            System.out.println("Starting to load base finder...");
            loadBaseFinder();
            System.out.println("Finished loading base finder...");
        }
        System.out.println("Starting server...");
        server();
        System.out.println("Finished starting server...");
    }
}
