package server;

import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import seeding.Constants;
import seeding.MyPreprocessor;
import seeding.WordVectorizer;
import server.tools.*;
import spark.Request;
import seeding.Database;
import spark.Response;
import spark.Session;
import tools.*;

import javax.imageio.ImageIO;

import static j2html.TagCreator.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    public static SimilarPatentFinder globalFinder;
    private static boolean failed = false;
    private static int DEFAULT_LIMIT = 25;
    private static final String SELECT_CANDIDATE_FORM_ID = "select-candidate-form";
    private static final String NEW_CANDIDATE_FORM_ID = "new-candidate-form";
    private static final String SELECT_BETWEEN_CANDIDATES_FORM_ID = "select-between-candidates-form";
    private static final String SELECT_ANGLE_BETWEEN_PATENTS = "select-angle-between-patents-form";
    private static Map<Integer, Pair<Boolean, String>> candidateSetMap;
    private static Map<Integer, List<Integer>> groupedCandidateSetMap;
    private static WordVectors wordVectors;
    private static TokenizerFactory tokenizer = new DefaultTokenizerFactory();
    static {
        tokenizer.setTokenPreProcessor(new MyPreprocessor());
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            globalFinder = new SimilarPatentFinder();
            wordVectors = WordVectorSerializer.loadGoogleModel(new File(Constants.GOOGLE_WORD_VECTORS_PATH), true);

        } catch(Exception e) {
            e.printStackTrace();
            failed = true;
        }
    }

    private static String getAndRemoveMessage(Session session) {
        String message = session.attribute("message");
        if(message!=null)session.removeAttribute("message");
        return message;
    }

    public static void server() {
        get("/", (req, res) -> templateWrapper(res, div().with(selectCandidateForm(), hr()), getAndRemoveMessage(req.session())));

        get("/new", (req, res) -> templateWrapper(res, createNewCandidateSetForm(), getAndRemoveMessage(req.session())));

        post("/create", (req, res) -> {
            if(req.queryParams("name")==null || (req.queryParams("patents")==null && req.queryParams("assignee")==null)) {
                req.session().attribute("message", "Invalid form parameters.");
                res.redirect("/new");
            } else {
                try {
                    String name = req.queryParams("name");
                    int id = Database.createCandidateSetAndReturnId(name);
                    SimilarPatentFinder patentFinder;
                    // try to get percentages from form
                    File file = new File(Constants.CANDIDATE_SET_FOLDER+id);
                    if(req.queryParams("assignee")!=null&&req.queryParams("assignee").trim().length()>0) {
                        patentFinder = new SimilarPatentFinder(Database.selectPatentNumbersFromAssignee(req.queryParams("assignee")),file,name);
                    } else if (req.queryParams("patents")!=null&&req.queryParams("patents").trim().length()>0) {
                        patentFinder = new SimilarPatentFinder(preProcess(req.queryParams("patents")), file, name);
                    } else {
                        req.session().attribute("message", "Patents and Assignee parameters were blank. Please choose one to fill out");
                        res.redirect("/new");
                        return null;
                    }
                    //new Emailer("Sucessfully created "+name);
                    req.session().attribute("candidateSet", patentFinder);
                    req.session().attribute("message", "Candidate set created.");
                    res.redirect("/");

                } catch(SQLException sql) {
                    sql.printStackTrace();
                    req.session().attribute("message", "Database Error");
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

        post("/similar_candidate_sets", (req, res) -> {
            res.type("application/json");
            if(req.queryParams("name1")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a first candidate set."));
            if(req.queryParamsValues("name2")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a second candidate set."));

            List<String> otherIds = Arrays.asList(req.queryParamsValues("name2"));
            if(otherIds.isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Must choose at least one other candidate set"));
            Integer id1 = Integer.valueOf(req.queryParams("name1"));
            if(id1 < 0 && globalFinder==null)
                return new Gson().toJson(new SimpleAjaxMessage("Unable to find first candidate set."));
            else {
                // both exist
                int limit = extractLimit(req);
                System.out.println("\tLimit: " + limit);
                List<SimilarPatentFinder> firstFinders = new ArrayList<>();
                if (id1 >= 0) {
                    if(groupedCandidateSetMap.containsKey(id1)) {
                        for(Integer id : groupedCandidateSetMap.get(id1)) {
                            String name = candidateSetMap.get(id).getSecond();
                            firstFinders.add(new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), name));
                        }
                    } else {
                        String name1 = candidateSetMap.get(id1).getSecond();
                        firstFinders.add(new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id1), name1));
                    }
                } else firstFinders = Arrays.asList(globalFinder);
                List<SimilarPatentFinder> secondFinders = new ArrayList<>();
                for(String id : otherIds) {
                    SimilarPatentFinder finder = null;
                    if (Integer.valueOf(id) >= 0) {
                        try {
                            if(groupedCandidateSetMap.containsKey(Integer.valueOf(id))) {
                                for(Integer groupedId : groupedCandidateSetMap.get(Integer.valueOf(id))) {
                                    System.out.println("CANDIDATE LOADING: " + candidateSetMap.get(groupedId).getSecond());
                                    finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + groupedId), candidateSetMap.get(groupedId).getSecond());
                                    if (finder != null && finder.getPatentList() != null && !finder.getPatentList().isEmpty()) {
                                        secondFinders.add(finder);
                                    }
                                }
                            } else {
                                System.out.println("CANDIDATE LOADING: " + candidateSetMap.get(Integer.valueOf(id)).getSecond());
                                finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), candidateSetMap.get(Integer.valueOf(id)).getSecond());
                                if (finder != null && finder.getPatentList() != null && !finder.getPatentList().isEmpty()) {
                                    secondFinders.add(finder);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }
                    } else {
                        secondFinders.add(globalFinder);
                    }
                }
                List<PatentList> patentLists = new ArrayList<>();
                double threshold = extractThreshold(req);
                boolean findDissimilar = extractFindDissimilar(req);
                for(SimilarPatentFinder first : firstFinders) {
                    patentLists.addAll(first.similarFromCandidateSets(secondFinders, threshold, limit, findDissimilar));
                }
                System.out.println("SIMILAR PATENTS FOUND!!!");
                patentLists.forEach(list->{
                    System.out.println("Sim "+list.getName1()+" "+list.getName2()+": "+list.getAvgSimilarity());
                    list.getPatents().forEach(p->{
                        System.out.println(p.getName()+": "+p.getSimilarity());
                    });
                });
                PatentResponse response = new PatentResponse(patentLists,findDissimilar);
                return new Gson().toJson(response);
            }

        });

        post("/angle_between_patents", (req,res) ->{
            res.type("application/json");
            if(req.queryParams("name1")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a first patent."));
            if(req.queryParams("name2")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a second patent."));

            String name1 = req.queryParams("name1");
            String name2 = req.queryParams("name2");

            if(name1==null || name2==null) return new Gson().toJson(new SimpleAjaxMessage("Please include two patents!"));

            Double sim = globalFinder.angleBetweenPatents(name1, name2);

            if(sim==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to find both patent vectors"));
            return new Gson().toJson(new SimpleAjaxMessage("Similarity between "+name1+" and "+name2+" is "+sim.toString()));
        });

        post("/similar_patents", (req, res) -> {
            ServerResponse response;
            String pubDocNumber = req.queryParams("patent");
            String text = req.queryParams("text");
            if((pubDocNumber == null || pubDocNumber.trim().length()==0) && (text==null||text.trim().length()==0)) return new Gson().toJson(new NoPatentProvided());
            boolean findDissimilar = extractFindDissimilar(req);
            if(req.queryParamsValues("name")==null || req.queryParamsValues("name").length==0)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a candidate set."));
            List<PatentList> patents=new ArrayList<>();
            SimilarPatentFinder currentPatentFinder = pubDocNumber!=null&&pubDocNumber.trim().length()>0 ? new SimilarPatentFinder(pubDocNumber) : new SimilarPatentFinder("Custom Text", new WordVectorizer(wordVectors).getVector(text));
            if(currentPatentFinder.getPatentList()==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to calculate vectors"));
            System.out.println("Searching for: " + pubDocNumber);
            int limit = extractLimit(req);
            boolean averageCandidates = extractAverageCandidates(req);
            System.out.println("\tLimit: " + limit);
            double threshold = extractThreshold(req);
            Arrays.asList(req.queryParamsValues("name")).forEach(name->{
                Integer id = null;
                try {
                    id = Integer.valueOf(name);
                    if(id!=null&&id>=0) {
                        if(groupedCandidateSetMap.containsKey(id)) {
                            // grouped
                            for (Integer integer : groupedCandidateSetMap.get(id)) {
                                SimilarPatentFinder finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + integer), candidateSetMap.get(integer).getSecond());
                                if (finder.getPatentList() != null && !finder.getPatentList().isEmpty())
                                    if(averageCandidates) {
                                        patents.addAll(currentPatentFinder.similarFromCandidateSet(finder, threshold, limit, findDissimilar));
                                    } else {
                                        patents.addAll(finder.similarFromCandidateSet(currentPatentFinder, threshold, limit, findDissimilar));
                                    }
                            }

                        } else {
                            SimilarPatentFinder finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), candidateSetMap.get(id).getSecond());
                            if (finder.getPatentList() != null && !finder.getPatentList().isEmpty())
                                if(averageCandidates) {
                                    patents.addAll(currentPatentFinder.similarFromCandidateSet(finder, threshold, limit, findDissimilar));
                                } else {
                                    patents.addAll(finder.similarFromCandidateSet(currentPatentFinder, threshold, limit, findDissimilar));
                                }
                        }
                    } else {
                        patents.addAll(globalFinder.similarFromCandidateSet(currentPatentFinder,threshold,limit,findDissimilar));
                    }
                } catch(Exception e) {
                    // bad format or something
                    e.printStackTrace();
                }
            });


            if(patents==null) response=new PatentNotFound(pubDocNumber);
            else if(patents.isEmpty()) response=new EmptyResults(pubDocNumber);
            else response=new PatentResponse(patents,findDissimilar);

            // Handle csv or json
            if(responseWithCSV(req)) {
                res.type("text/csv");
                return CSVHelper.to_csv(response);
            } else {
                res.type("application/json");
                return new Gson().toJson(response);
            }
        });


    }

    private static List<String> preProcess(String str) {
        return Arrays.asList(str.split("\\s+"));
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
                                h4(message),
                                hr(),
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
        return script().withText(
                "$(document).ready(function() { "
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
                        + "});");
    }


    private static void importCandidateSetFromDB() throws SQLException {
        ResultSet candidates = Database.selectAllCandidateSets();
        List<Integer> compdb = new ArrayList<>();
        List<Integer> etsi = new ArrayList<>();
        while(candidates.next()) {
            boolean hidden = false;
            if(candidates.getString(1).startsWith("ETSI -")) {
                hidden = true;
                etsi.add(candidates.getInt(2));
            } else if(candidates.getString(1).startsWith("CompDB -")) {
                hidden = true;
                compdb.add(candidates.getInt(2));

            }
            candidateSetMap.put(candidates.getInt(2),new Pair<>(hidden,candidates.getString(1)));
        }
        if(compdb.size()>0) {
            int size = Collections.max(candidateSetMap.keySet())+1;
            candidateSetMap.put(size,new Pair<>(false, "CompDB (by technology)"));
            groupedCandidateSetMap.put(size, compdb);
        }
        if(etsi.size()>0) {
            int size = Collections.max(candidateSetMap.keySet())+1;
            candidateSetMap.put(size,new Pair<>(false, "ETSI (by standard)"));
            groupedCandidateSetMap.put(size, etsi);
        }
    }

    private static Tag selectCandidateSetDropdown() {
        return selectCandidateSetDropdown("Select Candidate Set", "name", true);
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
                        candidateSetMap.entrySet().stream().sorted((o1,o2)->Integer.compare(o1.getKey(),o2.getKey())).map(entry->{if(entry.getValue().getFirst()) {return null; } if(entry.getKey()<0) return option().withText(entry.getValue().getSecond()).attr("selected","true").withValue(entry.getKey().toString()); else return option().withText(entry.getValue().getSecond()).withValue(entry.getKey().toString());}).filter(t->t!=null).collect(Collectors.toList())
                )
        );
    }

    private static Tag selectCandidateForm() {
        return div().with(formScript(SELECT_CANDIDATE_FORM_ID, "/similar_patents", "Search"),
                formScript(SELECT_BETWEEN_CANDIDATES_FORM_ID, "/similar_candidate_sets", "Search"),
                formScript(SELECT_ANGLE_BETWEEN_PATENTS, "/angle_between_patents", "Search"),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Find Similarity between two Patents"),
                                                form().withId(SELECT_ANGLE_BETWEEN_PATENTS).with(
                                                        label("Patent 1"),br(),input().withType("text").withName("name1"),br(),
                                                        label("Patent 2"),br(),input().withType("text").withName("name2"),br(),
                                                        br(),
                                                        button("Search").withId(SELECT_ANGLE_BETWEEN_PATENTS+"-button").withType("submit")
                                                )
                                        ),td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Find Similar Patents By Patent"),
                                                form().withId(SELECT_CANDIDATE_FORM_ID).with(selectCandidateSetDropdown(),
                                                        label("Similar To Patent"),br(),input().withType("text").withName("patent"),br(),
                                                        label("Similar To Custom Text"),br(),textarea().withName("text"),br(),
                                                        label("Average candidates"),br(),input().withType("checkbox").withName("averageCandidates"),br(),
                                                        label("Limit"),br(),input().withType("text").withName("limit"),br(),
                                                        label("Threshold"),br(),input().withType("text").withName("threshold"),br(),
                                                        label("Find most dissimilar"),br(),input().withType("checkbox").withName("findDissimilar"),br(),br(),
                                                        button("Search").withId(SELECT_CANDIDATE_FORM_ID+"-button").withType("submit")
                                                )
                                        ),td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Find Similar Patents to Candidate Set 2"),
                                                form().withId(SELECT_BETWEEN_CANDIDATES_FORM_ID).with(selectCandidateSetDropdown("Candidate Set 1","name1",false),
                                                        selectCandidateSetDropdown("Candidate Set 2", "name2",true),
                                                        label("Limit"),br(),input().withType("text").withName("limit"), br(),
                                                        label("Threshold"),br(),input().withType("text").withName("threshold"),br(),
                                                        label("Find most dissimilar"),br(),input().withType("checkbox").withName("findDissimilar"),br(),br(),
                                                        button("Search").withId(SELECT_BETWEEN_CANDIDATES_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                )
                        )
                ),
                br(),
                br(),
                a("Or create a new Candidate Set").withHref("/new")
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
                button("Create").withId(NEW_CANDIDATE_FORM_ID+"-button").withType("submit")
        );
    }

    private static boolean responseWithCSV(Request req) {
        String param = req.queryParams("format");
        return (param!=null && param.contains("csv"));
    }

    private static int extractLimit(Request req) {
        try {
            return Integer.valueOf(req.queryParams("limit"));
        } catch(Exception e) {
            System.out.println("No limit parameter specified... using default");
            return DEFAULT_LIMIT;
        }
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
            return -1D;
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

    public static void main(String[] args) {
        assert !failed : "Failed to load similar patent finder!";
        server();
    }
}
