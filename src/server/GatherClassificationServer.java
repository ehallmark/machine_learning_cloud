package server;

import com.google.gson.Gson;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import server.tools.SimpleAjaxMessage;
import similarity_models.class_vectors.WIPOSimilarityFinder;
import spark.Request;
import spark.Response;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.attributes.classification.WIPOTechnologyClassifier;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.attributes.WIPOClassificationAttribute;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static spark.Spark.*;

/**
 * Created by ehallmark on 11/1/16.
 */
public class GatherClassificationServer {
    private static ClassificationAttr techTagger = TechTaggerNormalizer.getDefaultTechTagger();
    private static ClassificationAttr wipoTagger = new WIPOTechnologyClassifier();
    public static void StartServer() throws Exception {
        get("/predict_patents", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> techTagger.attributesFor(patents, tagLimit)));
        post("/predict_patents", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> techTagger.attributesFor(patents, tagLimit)));
        post("/predict_wipo", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> wipoTagger.attributesFor(patents, tagLimit)));
        get("/predict_wipo", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> wipoTagger.attributesFor(patents, tagLimit)));
    }


    private static String handleRequest(Request req, Response res, Function2<Collection<String>,Integer,List<Pair<String,Double>>> function) throws Exception {
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

        List<Pair<String,Double>> topTags = new ArrayList<>(function.apply(patents,tagLimit));

        long designCount = patents.stream().filter(p->p.startsWith("D")).count();
        if(designCount>0) topTags.add(new Pair<>("Design",new Double(designCount)/patents.size()));

        topTags = topTags.stream().sorted((p1,p2)->Double.compare(p2.getSecond(),p1.getSecond())).limit(tagLimit).collect(Collectors.toList());

        // return results
        if(topTags.isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Unable to predict any technologies"));
        return new Gson().toJson(topTags.stream().map(tag->tag.getFirst()).collect(Collectors.toList()));
    }


    public static void main(String[] args) throws Exception {
        port(4567);
        Database.initializeDatabase();
        StartServer();
    }

    interface Function2<T1,T2,T3> {
        T3 apply(T1 t1, T2 t2);
    }
}
