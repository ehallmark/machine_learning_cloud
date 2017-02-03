package server;

import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import seeding.Constants;
import seeding.Database;
import server.tools.SimpleAjaxMessage;
import spark.Request;
import spark.Response;
import tools.PortfolioList;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static spark.Spark.*;

/**
 * Created by ehallmark on 11/1/16.
 */
public class GatherClassificationServer {
    public static List<SimilarPatentFinder> gatherFinders;
    private static WeightLookupTable<VocabWord> lookupTable;

    static {
        try {
            if(SimilarPatentServer.paragraphVectors==null) {
                SimilarPatentServer.loadLookupTable();
            }

            lookupTable = SimilarPatentServer.paragraphVectors.lookupTable();


            //lookupTable = org.deeplearning4j.models.embeddings.loader.WordVectorSerializer.readParagraphVectorsFromText(new File("wordvectorexample2.txt")).getLookupTable();
            Database.setupSeedConn();
            gatherFinders = new ArrayList<>();

            Database.getGatherTechMap().forEach((tech,patents)->{
                try {
                    System.out.println("CANDIDATE LOADING: " + tech);
                    Set<String> data = new HashSet<>();
                    patents.forEach(patent->{
                        data.add(patent);
                        data.addAll(Database.classificationsFor(patent));
                    });
                    SimilarPatentFinder finder = new SimilarPatentFinder(data, tech, lookupTable);
                    if (finder != null && finder.getPatentList().size() > 0) {
                        gatherFinders.add(finder);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }



    public static void StartServer() {
        port(6969);
        before((request, response) -> {
            boolean authenticated = true;

            // authentication not yet implemented
            // TODO
            if (!authenticated) {
                halt(401, "You are not welcome here");
            }
        });

        get("/predict_patents", (req,res) ->handleRequest( req, res));
        post("/predict_patents", (req,res) ->handleRequest( req, res));


    }


    private static String handleRequest(Request req, Response res) throws Exception {
        res.type("application/json");
        if(req.queryParamsValues("patents")==null || req.queryParamsValues("patents").length==0)  return new Gson().toJson(new SimpleAjaxMessage("Please provide at least one patent."));

        List<String> patents = Arrays.asList(req.queryParams("patents"));
        int tmp = 3;
        if(req.queryParams("limit")!=null && req.queryParams("limit").length()>0) {
            try {
                tmp=Integer.valueOf(req.queryParams("limit"));
            } catch(Exception e) {
            }
        }
        final int tagLimit = tmp;

        // make sure patents exist
        SimilarPatentFinder tmpFinder = new SimilarPatentFinder(patents,String.valueOf(new Date().getTime()),lookupTable);

        // run model
        List<String> topTags = tmpFinder.similarFromCandidateSets(gatherFinders,0.75,100,new HashSet<>(), PortfolioList.Type.patents).stream()
                .sorted((s1,s2)->Double.compare(s2.getAvgSimilarity(),s1.getAvgSimilarity()))
                .limit(tagLimit)
                .map(result->result.getName2())
                .collect(Collectors.toList());

        // return results
        if(topTags.isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Unable to predict any technologies"));
        return new Gson().toJson(topTags);
    }

    public static void main(String[] args) throws Exception {
        StartServer();
    }
}
