package server;

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
    private static final String PREDICT_KEYWORDS_FORM_ID = "predict-keywords-form";
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
        //paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
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

    public static void server() {
        port(4568);
        get("/", (req, res) -> {
            try {
                return templateWrapper(res, div().with(selectCandidateForm(), hr()), getAndRemoveMessage(req.session()));
            } catch(Exception e) {
                new Emailer(e.toString());
                System.out.println("EXCEPTION: "+e.toString());
                return null;
            }
        });

        get("/new", (req, res) -> templateWrapper(res, createNewCandidateSetForm(), getAndRemoveMessage(req.session())));

        post("/assignee_asset_count", (req, res) -> {
            res.type("application/json");
            String assigneeStr = req.queryParams("assignee");
            if(assigneeStr==null||assigneeStr.trim().isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));

            String[] assignees = assigneeStr.split("\\n");
            if(assignees==null||assignees.length==0) return new Gson().toJson(new SimpleAjaxMessage("Please enter at least one assignee"));
                StringJoiner sj = new StringJoiner("\n");
                for(String assignee : assignees) {
                    assignee=assignee.trim();
                    if(assignee.isEmpty()) continue;
                    try {
                        String data = assignee+"\t"+String.valueOf(Database.getAssetCountFor(assignee));
                        sj.add(data);
                    } catch(SQLException sql) {
                        sql.printStackTrace();
                        String data = assignee+"\t"+"0";
                        sj.add(data);
                    }
                }

                return new Gson().toJson(new SimpleAjaxMessage(sj.toString()));
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

        post("/predict_tokens", (req, res) ->{
            res.type("application/json");

            if(req.queryParams("name")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a first candidate set."));

            int limit = extractLimit(req);

            Integer id1 = Integer.valueOf(req.queryParams("name"));

            if(id1 < 0 && globalFinder==null)
                return new Gson().toJson(new SimpleAjaxMessage("Unable to find first candidate set."));
            else {

                // get similar patent finders
                List<SimilarPatentFinder> firstFinders;
                if (id1 >= 0) {
                    firstFinders = new ArrayList<>();
                    if (groupedCandidateSetMap.containsKey(id1)) {
                        for (Integer id : groupedCandidateSetMap.get(id1)) {
                            String assignee = candidateSetMap.get(id).getSecond();
                            assignee = assignee.replaceFirst(candidateSetMap.get(Integer.valueOf(id1)).getSecond(), "").trim();
                            firstFinders.add(new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), assignee, paragraphVectors.lookupTable()));
                        }
                    } else {
                        String name1 = candidateSetMap.get(id1).getSecond();
                        firstFinders.add(new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id1), name1, paragraphVectors.lookupTable()));
                    }
                } else firstFinders = Arrays.asList(globalFinder);

                return new Gson().toJson(new SimpleAjaxMessage(div().with(firstFinders.stream().map(similarPatentFinder -> {
                    return div().with(similarPatentFinder.getPatentList().stream().map(patent->{
                        if(!paragraphVectors.hasWord(patent.getName())) return null;

                        Collection<String> words = null;
                        try {
                            words = paragraphVectors.wordsNearest(patent.getVector(), limit);
                        } catch(Exception e) {
                            e.printStackTrace();
                            new Emailer("Error finding closest words");
                        }
                        if(words!=null) {
                            return table().with(thead().with(tr().with(
                                    th(similarPatentFinder.getName()).attr("colspan","2"))),
                                    tbody().with(words.stream()
                                            .map(word -> tr().with(td(patent.getName()), td(word)))
                                            .collect(Collectors.toList())));
                        } else {
                            return null;
                        }
                    }).collect(Collectors.toList()));
                }).filter(v->v!=null).collect(Collectors.toList())).render()));

            }
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
                        new SimilarPatentFinder(preProcess(req.queryParams("patents")), file, name, paragraphVectors.lookupTable());
                    } else if (req.queryParams("article")!=null&&req.queryParams("article").trim().length()>0) {
                        new SimilarPatentFinder(name, file, tokenizerFactory.create(new PhrasePreprocessor().preProcess(req.queryParams("article"))).getTokens(),paragraphVectors);
                    } else {
                        req.session().attribute("message", "Patents and Assignee parameters were blank. Please choose one to fill out");
                        res.redirect("/new");
                        return null;
                    }
                    req.session().attribute("message", "Candidate set created.");
                    res.redirect("/");

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

            boolean additionalModel1 = extractBool(req, "use_additional_model_1");
            int limitAdditionalModel1 = extractInt(req, "additional_model_1_limit", limit);
            List<String> additionalModel1Ids = additionalModel1 ? Arrays.asList(req.queryParamsValues("additional_model_1")) : null;

            boolean additionalModel2 = extractBool(req, "use_additional_model_2");
            int limitAdditionalModel2 = extractInt(req, "additional_model_2_limit", limit);
            List<String> additionalModel2Ids = additionalModel2 ? Arrays.asList(req.queryParamsValues("additional_model_2")) : null;

            List<String> otherIds = Arrays.asList(req.queryParamsValues("name2"));
            if(otherIds.isEmpty()&&!gatherValue) return new Gson().toJson(new SimpleAjaxMessage("Must choose at least one other candidate set"));

            Integer id1 = Integer.valueOf(req.queryParams("name1"));

            // get more detailed params
            String clientName = req.queryParams("client");
            if(clientName==null) clientName="";
            int tagLimit = extractTagLimit(req);
            int groupLimit = extractGroupLimit(req);
            boolean merge = extractBool(req,"merge");
            boolean limitGroups = extractAverageCandidates(req);
            Integer minPatentNum = extractMinPatentNumber(req);
            double threshold = extractThreshold(req);
            boolean findDissimilar = extractFindDissimilar(req);
            List<String> badAssignees = preProcess(req.queryParams("assigneeFilter"),";");
            Set<String> badAssets = new HashSet<>(preProcess(req.queryParams("assetFilter")));
            List<String> highlightAssignees = preProcess(req.queryParams("assigneeHighlighter"),";").stream().map(str->str.toUpperCase()).collect(Collectors.toList());
            String title = req.queryParams("title");
            if(title==null)title="";
            boolean FilterAssigneesByPatentCount = req.queryParams("filterAssigneesByPatents")!=null&&req.queryParams("filterAssigneesByPatents").startsWith("on");
            boolean switchCandidateSets = req.queryParams("switchCandidateSets")!=null&&req.queryParams("switchCandidateSets").startsWith("on");
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
                Pair<List<SimilarPatentFinder>,List<SimilarPatentFinder>> firstAndSecondFinders = getFirstAndSecondFinders(firstFinders,otherIds,switchCandidateSets,merge);
                PatentList patentList = runPatentFinderModel(title, firstAndSecondFinders.getFirst(), firstAndSecondFinders.getSecond(), 0, limit, threshold, findDissimilar, minPatentNum, limitGroups, groupLimit, badAssets, badAssignees,allowResultsFromOtherCandidateSet);

                final int assigneePatentLimit = 100;
                if(FilterAssigneesByPatentCount) {
                    Set<String> largeAssigneeSet = new HashSet<>();
                    Database.selectPatentCountFromAssignee(patentList.getAssignees().stream().map(a->a.getName()).collect(Collectors.toList())).entrySet()
                    .forEach(e->{
                        if(e.getValue() > assigneePatentLimit) {
                            largeAssigneeSet.add(e.getKey());
                        }
                    });
                    patentList.setPatents(patentList.getPatents().stream()
                        .filter(patent->!largeAssigneeSet.contains(patent.getAssignee().toUpperCase()))
                        .collect(Collectors.toList()));
                }


                if(additionalModel1) {
                    int tagIdx = 1;
                    Pair<List<SimilarPatentFinder>,List<SimilarPatentFinder>> additionalModel1Finders = getFirstAndSecondFinders(Arrays.asList(new SimilarPatentFinder(patentList.getPatentStrings(),null,patentList.getName1(),paragraphVectors.lookupTable())),additionalModel1Ids,switchCandidateSets,merge);
                    PatentList additionalModel1List = runPatentFinderModel(title, additionalModel1Finders.getFirst(), additionalModel1Finders.getSecond(), tagIdx, limitAdditionalModel1, threshold, findDissimilar, minPatentNum, limitGroups, groupLimit, badAssets, badAssignees,allowResultsFromOtherCandidateSet);
                    Map<String,Map<String,Double>> orderedTags = new HashMap<>();
                    additionalModel1List.getPatents().forEach(patent->{
                        orderedTags.put(patent.getName(),patent.getTags());
                    });
                    List<AbstractPatent> taggedPatents = new ArrayList<>();
                    patentList.getPatents().forEach(patent->{
                        if (orderedTags.containsKey(patent.getName())) {
                            patent.setTags(orderedTags.get(patent.getName()));
                            taggedPatents.add(patent);
                        }
                    });
                    patentList.setPatents(taggedPatents);
                }

                if(additionalModel2) {
                    int tagIdx = 2;
                    Pair<List<SimilarPatentFinder>,List<SimilarPatentFinder>> additionalModel2Finders = getFirstAndSecondFinders(Arrays.asList(new SimilarPatentFinder(patentList.getPatentStrings(),null,patentList.getName1(),paragraphVectors.lookupTable())),additionalModel2Ids,switchCandidateSets,merge);
                    PatentList additionalModel2List = runPatentFinderModel(title, additionalModel2Finders.getFirst(), additionalModel2Finders.getSecond(),tagIdx, limitAdditionalModel2, threshold, findDissimilar, minPatentNum, limitGroups, groupLimit, badAssets, badAssignees,allowResultsFromOtherCandidateSet);
                    Map<String,Map<String,Double>> orderedTags = new HashMap<>();
                    additionalModel2List.getPatents().forEach(patent->{
                        orderedTags.put(patent.getName(),patent.getTags());
                    });
                    List<AbstractPatent> taggedPatents = new ArrayList<>();
                    patentList.getPatents().forEach(patent->{
                        if (orderedTags.containsKey(patent.getName())) {
                            patent.setTags(orderedTags.get(patent.getName()));
                            taggedPatents.add(patent);
                        }
                    });
                    patentList.setPatents(taggedPatents);
                }


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

        post("/angle_between_patents", (req,res) ->{
            res.type("application/json");
            if(req.queryParams("name1")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a first patent."));
            if(req.queryParams("name2")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a second patent."));

            String name1 = req.queryParams("name1");
            String name2 = req.queryParams("name2");

            if(name1==null || name2==null) return new Gson().toJson(new SimpleAjaxMessage("Please include two patents!"));

            Double sim = globalFinder.angleBetweenPatents(name1, name2, paragraphVectors.lookupTable());

            if(sim==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to find both patent vectors"));
            return new Gson().toJson(new SimpleAjaxMessage("Similarity between "+name1+" and "+name2+" is "+sim.toString()));
        });

    }

    private static Pair<List<SimilarPatentFinder>,List<SimilarPatentFinder>> getFirstAndSecondFinders(List<SimilarPatentFinder> firstFinders, List<String> otherIds, boolean switchCandidateSets, boolean merge) throws Exception {
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
                        //if(assignee.startsWith("Wiki - "))assignee=assignee.replaceFirst("Wiki - ","");
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
        // merge if necessary
        if(merge) {
            List<SimilarPatentFinder> tmp = new ArrayList<>(1);
            tmp.add(secondFinders.get(0));
            tmp.get(0).setName("Merged Group");
            for(int i = 1; i < secondFinders.size(); i++) {
                tmp.get(0).getPatentList().addAll(secondFinders.get(i).getPatentList());
            }
            secondFinders = tmp;
        }

        return new Pair<>(firstFinders, secondFinders);
    }

    private static PatentList runPatentFinderModel(String name, List<SimilarPatentFinder> firstFinders, List<SimilarPatentFinder> secondFinders, int tagIdx, int limit, double threshold, boolean findDissimilar, Integer minPatentNum, boolean limitGroups, int groupLimit, Set<String> badAssets, List<String> badAssignees, boolean allowResultsFromOtherCandidateSet) {
        List<PatentList> patentLists = new ArrayList<>();
        try {
            for(SimilarPatentFinder first : firstFinders) {
                try {
                    List<PatentList> similar = first.similarFromCandidateSets(secondFinders, threshold, limit, findDissimilar, minPatentNum, badAssets,allowResultsFromOtherCandidateSet);
                    if(limitGroups) {
                        similar=similar.stream().sorted().limit(groupLimit).collect(Collectors.toList());
                    }

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

        return mergePatentLists(patentLists,badAssignees, name, tagIdx);
    }

    private static PatentList mergePatentLists(List<PatentList> patentLists, List<String> assigneeFilter, String name, int tagIdx) {
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


    private static List<String> preProcess(String str) {
        return preProcess(str, "\\s+");
    }

    private static List<String> preProcess(String toSplit, String delim) {
        if(toSplit==null||toSplit.trim().length()==0) return new ArrayList<>();
        return Arrays.asList(toSplit.split(delim)).stream().filter(str->str!=null).map(str->str.trim().replaceFirst("US","")).collect(Collectors.toList());
    }

    private static Tag templateWrapper(Response res, Tag form, String message) {
        res.type("text/html");
        if(message==null)message="";
        return html().with(
                head().with(
                        //title(title),
                        script().attr("src","https://ajax.googleapis.com/ajax/libs/jquery/3.0.0/jquery.min.js"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().attr("OnKeyPress","return disableKeyPress(event);").with(
                        div().attr("style", "width:80%; padding: 2% 10%;").with(
                                a().attr("href", "/").with(
                                        img().attr("src", "/images/brand.png")
                                ),
                                //h2(title),
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
    private static Tag formScript(String formId, String url, String buttonText) {
        return formScript(formId,url,buttonText,true);
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
                            //+ "    alert(data.message);"
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

    private static Tag selectCandidateForm() {
        return div().with(//formScript(SELECT_CANDIDATE_FORM_ID, "/similar_patents", "Search"),
                formScript(SELECT_BETWEEN_CANDIDATES_FORM_ID, "/similar_candidate_sets", "Search", false),
                formScript(PREDICT_KEYWORDS_FORM_ID, "/predict_tokens", "Search", true),
                formScript(ASSIGNEE_ASSET_COUNT_FORM_ID, "/assignee_asset_count", "Search", true),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                  td().attr("style","width:33%; vertical-align: top;").with(
                                          a("Create a new Candidate Set").withHref("/new"),
                                          h3("Get Asset Count for Assignees (Estimation Only)"),
                                          h4("Please place each assignee on a separate line"),
                                          form().withId(ASSIGNEE_ASSET_COUNT_FORM_ID).with(
                                                  label("Assignee"),br(),textarea().withName("assignee"), br(),
                                                  button("Search").withId(ASSIGNEE_ASSET_COUNT_FORM_ID+"-button").withType("submit")
                                          ),hr(),
                                          h3("Predict Keywords"),
                                          form().withId(PREDICT_KEYWORDS_FORM_ID).with(
                                                  selectCandidateSetDropdown("Candidate Set","name",false),
                                                  label("Limit"),br(),input().withType("text").withName("limit"), br(),
                                                  button("Search").withId(PREDICT_KEYWORDS_FORM_ID+"-button").withType("submit")
                                          ),hr(),
                                          h3("Generate Assignee Mining Spreadsheet"),
                                          form().withId(SELECT_BETWEEN_CANDIDATES_FORM_ID).with(
                                                  h3("Main options"),
                                                  selectCandidateSetDropdown("Candidate Set 1","name1",false),
                                                  selectCandidateSetDropdown("Candidate Set 2", "name2",true),
                                                  label("Search Type (eg. 'Focused Search')"),br(),input().withType("text").withName("title"), br(),
                                                  label("Patent Limit"),br(),input().withType("text").withName("limit"), br(),
                                                  label("Min Patent Number"),br(),input().withType("text").withName("min_patent"),br(),
                                                  label("Threshold"),br(),input().withType("text").withName("threshold"),br(),
                                                  label("Switch candidate sets"),br(),input().withType("checkbox").withName("switchCandidateSets"),br(),
                                                  label("Filter large assignees"),br(),input().withType("checkbox").withName("filterAssigneesByPatents"),br(),
                                                  label("Largest portfolio size (only if filtering by large assignee)"),br(),input().withType("text").withName("filterAssigneesByPatents"),br(),
                                                  hr(),
                                                  h3("Contact Info (primarily for the cover page)"),
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
                                                  hr(),
                                                  h3("Advanced Options"),
                                                  label("Allow results from other comparison set?"),br(),input().withType("checkbox").withName("allowResultsFromOtherCandidateSet"),br(),
                                                  label("Limit groups?"),br(),input().withType("checkbox").withName("averageCandidates"),br(),
                                                  label("Group Limit"),br(),input().withType("text").withName("group_limit"), br(),
                                                  label("Tag Limit"),br(),input().withType("text").withName("tag_limit"), br(),
                                                  label("Find most dissimilar"),br(),input().withType("checkbox").withName("findDissimilar"),br(),
                                                  //label("Rank assignees"),br(),input().withType("checkbox").withName("matchAssignees"),br(),
                                                  label("Merge"),br(),input().withType("checkbox").withName("merge"),br(),
                                                  label("Tag Index"),br(),input().withType("text").withName("tag_index"), br(),
                                                  label("Use Additional Model 1"),br(),input().withType("checkbox").withName("use_additional_model_1"), br(),
                                                  selectCandidateSetDropdown("Additional Model 1", "additional_model_1",true),
                                                  label("Result Limit"),br(),input().withType("text").withName("additional_model_1_limit"), br(),
                                                  label("Use Additional Model 2"),br(),input().withType("checkbox").withName("use_additional_model_2"), br(),
                                                  selectGatherTechnologyDropdown("From Gather Technologies", "additional_model_2"),
                                                  label("Result Limit"),br(),input().withType("text").withName("additional_model_2_limit"), br(),
                                                  label("Gather Value Model (Special Model)"),br(),input().withType("checkbox").withName("gather_value"),br(),
                                                  label("Asset Filter (space separated)"),br(),textarea().attr("selected","true").withName("assetFilter"),br(),
                                                  label("Assignee Filter (; separated)"),br(),textarea().withName("assigneeFilter"),br(),
                                                  label("Assignee Highlighter (; separated)"),br(),textarea().withName("assigneeHighlighter"),br(),hr(),
                                                  label("Require keywords"),br(),textarea().withName("required_keywords"),br(),hr(),
                                                  label("Avoid keywords"),br(),textarea().withName("avoided_keywords"),br(),hr(),
                                                  button("Search").withId(SELECT_BETWEEN_CANDIDATES_FORM_ID+"-button").withType("submit")


                                          )                                 )
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

    private static int extractGroupLimit(Request req) {
        try {
            return Integer.valueOf(req.queryParams("group_limit"));
        } catch(Exception e) {
            System.out.println("No limit parameter specified... using default");
            return DEFAULT_LIMIT;
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

    private static boolean extractAverageCandidates(Request req) {
        try {
            return (req.queryParams("averageCandidates")==null||!req.queryParams("averageCandidates").startsWith("on")) ? false : true;
        } catch(Exception e) {
            System.out.println("No averageCandidates parameter specified... using default");
            return false;
        }
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


    private static boolean extractFindDissimilar(Request req) {
        try {
            return (req.queryParams("findDissimilar")==null||!req.queryParams("findDissimilar").startsWith("on")) ? false : true;
        } catch(Exception e) {
            System.out.println("No findDissimilar parameter specified... using default");
            return false;
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
        loadLookupTable();
        if(!Arrays.asList(args).contains("dontLoadBaseFinder")) {
            loadBaseFinder();
        }
        server();
    }
}
