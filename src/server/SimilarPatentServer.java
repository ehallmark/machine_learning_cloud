package server;

import analysis.Patent;
import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import spark.Request;
import seeding.Database;
import tools.CSVHelper;
import tools.PatentList;

import java.util.List;

import static spark.Spark.get;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    public static SimilarPatentFinder finder;
    private static boolean failed = false;
    private static int DEFAULT_LIMIT = 25;
    private static boolean DEFAULT_STRICTNESS = false;
    private static Patent.Type DEFAULT_TYPE = Patent.Type.ALL;
    static {
        try {
            Database.setupSeedConn();
            finder = new SimilarPatentFinder();
        } catch(Exception e) {
            e.printStackTrace();
            failed = true;
        }
    }

    public static void server() {
        get("/similar_patents", (req, res) -> {
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
