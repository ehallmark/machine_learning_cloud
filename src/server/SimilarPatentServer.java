package server;

import analysis.Patent;
import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import j2html.tags.Tag;
import seeding.Constants;
import server.tools.*;
import spark.Request;
import seeding.Database;
import spark.Response;
import spark.Session;
import tools.CSVHelper;
import tools.PatentList;

import javax.imageio.ImageIO;

import static j2html.TagCreator.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    public static SimilarPatentFinder finder;
    private static boolean failed = false;
    private static int DEFAULT_LIMIT = 25;
    private static boolean DEFAULT_STRICTNESS = true; // faster
    private static Patent.Type DEFAULT_TYPE = Patent.Type.ALL;
    private static final String SELECT_CANDIDATE_FORM_ID = "select-candidate-form";
    private static final String NEW_CANDIDATE_FORM_ID = "new-candidate-form";
    private static Map<String, Integer> candidateSetMap;
    static {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            finder = new SimilarPatentFinder();
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
        get("/", (req, res) -> templateWrapper(res, selectCandidateForm(), true, SELECT_CANDIDATE_FORM_ID, "/select", getAndRemoveMessage(req.session())));

        get("/new", (req, res) -> templateWrapper(res, createNewCandidateSetForm(), false, null, null, getAndRemoveMessage(req.session())));

        post("/create", (req, res) -> {
            if(req.queryParams("name")==null || req.queryParams("patents")==null) {
                req.session().attribute("message", "Invalid form parameters.");
                res.redirect("/new");
            } else {
                try {
                    int id = Database.createCandidateSetAndReturnId(req.queryParams("name"));
                    req.session().attribute("candidateSet", new SimilarPatentFinder(preProcess(req.queryParams("patents")), new File(Constants.CANDIDATE_SET_FOLDER+id)));
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

        post("/select", (req, res) -> {
            res.type("application/json");
            if(req.queryParams("id")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a candidate set."));
            int id = Integer.valueOf(req.queryParams("id"));
            if(id > 0) {
                // exists
                req.session().attribute("candidateSet", new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER+id)));
                return new Gson().toJson(new SimpleAjaxMessage("Candidate set selected."));
            } else {
                return new Gson().toJson(new SimpleAjaxMessage("Unable to find candidate set."));
            }
        });

        post("/similar_patents", (req, res) -> {
            ServerResponse response;
            String pubDocNumber = req.queryParams("patent");
            List<PatentList> patents=null;
            if(pubDocNumber == null) response=new NoPatentProvided();
            else {
                System.out.println("Searching for: " + pubDocNumber);
                int limit = extractLimit(req);
                System.out.println("\tLimit: " + limit);
                Patent.Type type = extractType(req);
                System.out.println("\tType: " + type.toString());
                boolean strictness = extractStrictness(req);
                System.out.println("\tStrictness: " + strictness);
                patents = finder.findSimilarPatentsTo(pubDocNumber, type, limit, strictness);
            }
            if(patents==null) response=new PatentNotFound(pubDocNumber);
            else if(patents.isEmpty()) response=new EmptyResults(pubDocNumber);
            else response=new PatentResponse(patents,pubDocNumber);

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
        // try to break on spaces
        String chunk = str.substring(0,Math.min(20,str.length()));
        if(chunk.split("\\s+").length==0) {
            // try commas
            if(chunk.split(",").length==0) {
                // try US -- last fail safe
                return Arrays.asList(str.toUpperCase().split("US"));
            } else {
                // commas present but no spaces
                if(chunk.split(",").length > 3) {
                    // too many commas -- commas must be present inside numbers :(
                    return Arrays.asList(str.toUpperCase().split("US")).stream().map(s->s.replaceAll(",","")).collect(Collectors.toList());
                } else {
                    // simple comma split
                    return Arrays.asList(str.toUpperCase().split(",")).stream().map(s->s.replaceFirst("US","")).collect(Collectors.toList());
                }
            }
        } else {
            // split on spaces
            return Arrays.asList(str.split("\\s+")).stream().map(s->s.toUpperCase().replaceAll(",","").replaceFirst("US","").trim()).collect(Collectors.toList());
        }
    }

    private static Tag templateWrapper(Response res, Tag form, boolean withAjax, String formId, String url, String message) {
        res.type("text/html");
        if(message==null)message="";
        Tag script = script();
        if(withAjax) script = formScript(formId, url);
        return html().with(
                head().with(
                        //title(title),
                        script().attr("src","https://ajax.googleapis.com/ajax/libs/jquery/3.0.0/jquery.min.js"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().attr("OnKeyPress","return disableKeyPress(event);").with(
                        script,
                        div().attr("style", "width:80%; padding: 2% 10%;").with(
                                a().attr("href", "/").with(
                                        img().attr("src", "/images/brand.png")
                                ),
                                //h2(title),
                                h3(message),
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

    private static Tag formScript(String formId, String url) {
        return script().withText(
                "$(document).ready(function() { "
                        + "$('#"+formId+"').submit(function(e) {"
                        + "$('#"+formId+"-button').attr('disabled',true).text('Searching...');"
                        + "var url = '"+url+"'; "
                        + "$.ajax({"
                        + "type: 'POST',"
                        + "url: url,"
                        + "data: $('#"+formId+"').serialize(),"
                        + "success: function(data) { "
                        + "$('#results').html(data.results); "
                        + "$('#"+formId+"-button').attr('disabled',false).text('Search');"
                        + "}"
                        + "});"
                        + "e.preventDefault(); "
                        + "});"
                        + "});");
    }

    private static void importCandidateSetFromDB() throws SQLException {
        ResultSet candidates = Database.selectAllCandidateSets();
        while(candidates.next()) {
            candidateSetMap.put(candidates.getString(1),candidates.getInt(2));
        }
    }


    private static Tag selectCandidateSetDropdown() {
        candidateSetMap = new HashMap<>();
        try {
            importCandidateSetFromDB();
        } catch(SQLException sql ) {
            sql.printStackTrace();
            return label("ERROR:: Unable to load candidate set.");
        }
        return div().with(
                label("Select Candidate Set"),
                br(),
                select().withName("name").with(
                        candidateSetMap.entrySet().stream().map(entry->option().withText(entry.getKey()).withValue(entry.getValue().toString())).collect(Collectors.toList())
                )
        );
    }

    private static Tag selectCandidateForm() {
        return form().withId(SELECT_CANDIDATE_FORM_ID).withAction("/select").withMethod("post").with(selectCandidateSetDropdown(),
                button("Select").withId(SELECT_CANDIDATE_FORM_ID+"-button").withType("submit"),
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
                label("Patents (space separated)"), br(),
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

    private static Patent.Type extractType(Request req) {
        try {
            return Patent.Type.valueOf(req.queryParams("type").toUpperCase());
        } catch(Exception e) {
            System.out.println("No type parameter specified... using default");
            return DEFAULT_TYPE;
        }
    }

    private static boolean extractStrictness(Request req) {
        try {
            return req.queryParams("strict").startsWith("t");
        } catch(Exception e) {
            System.out.println("No strictness parameter specified... using default");
            return DEFAULT_STRICTNESS;
        }
    }

    public static void main(String[] args) {
        assert !failed : "Failed to load similar patent finder!";
        server();
    }
}
