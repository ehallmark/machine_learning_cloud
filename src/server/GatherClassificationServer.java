package server;

import com.google.gson.Gson;
import org.deeplearning4j.berkeley.Pair;
import server.tools.SimpleAjaxMessage;
import spark.Request;
import spark.Response;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.stream.Collectors;

import static spark.Spark.*;

/**
 * Created by ehallmark on 11/1/16.
 */
public class GatherClassificationServer {
    private static ClassificationAttr tagger = TechTaggerNormalizer.getDefaultTechTagger();
    public static void StartServer() throws Exception{

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
        if(req.queryParams("patents")==null || req.queryParams("patents").length()==0)  return new Gson().toJson(new SimpleAjaxMessage("Please provide at least one patent."));

        Set<String> patents = new HashSet<>(Arrays.asList(req.queryParams("patents").split("\\s+")));
        int tmp = 3;
        if(req.queryParams("limit")!=null && req.queryParams("limit").length()>0) {
            try {
                tmp=Integer.valueOf(req.queryParams("limit"));
            } catch(Exception e) {
            }
        }
        final int tagLimit = tmp;

        // make sure patents exist
        // run model
        List<Pair<String,Double>> topTags = tagger.attributesFor(PortfolioList.abstractPorfolioList(patents, PortfolioList.Type.patents), tagLimit);

        // return results
        if(topTags.isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Unable to predict any technologies"));
        return new Gson().toJson(topTags.stream().map(tag->tag.getFirst()).collect(Collectors.toList()));
    }

    public static void main(String[] args) throws Exception {
        port(4567);
        StartServer();
    }
}
