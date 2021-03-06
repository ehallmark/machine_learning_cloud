package user_interface.server;

import com.google.gson.Gson;
import models.classification_models.TechnologyClassifier;
import org.nd4j.linalg.primitives.Pair;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import spark.Response;
import user_interface.server.tools.SimpleAjaxMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static spark.Spark.*;

/**
 * Created by ehallmark on 11/1/16.
 */
public class GatherClassificationServer {
    private static TechnologyClassifier techTagger = new TechnologyClassifier(Attributes.TECHNOLOGY);
    private static TechnologyClassifier wipoTagger = new TechnologyClassifier(Attributes.WIPO_TECHNOLOGY);
    public static void StartServer() throws Exception {
        get("/predict_patents", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> techTagger.attributesFor(patents,tagLimit)));
        post("/predict_patents", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> techTagger.attributesFor(patents,tagLimit)));

        post("/predict_wipo", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> wipoTagger.attributesFor(patents, tagLimit)));
        get("/predict_wipo", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> wipoTagger.attributesFor(patents, tagLimit)));
    }


    private static String handleRequest(Request req, Response res, Function2<Collection<String>,Integer,List<Pair<String,Double>>> function) throws Exception {
        try {
            res.type("application/json");
            String patentStr = req.queryParams("assets");
            if (patentStr==null || patentStr.length() == 0)
                return new Gson().toJson(new SimpleAjaxMessage("Please provide at least one patent."));

            List<String> patents = Arrays.asList(patentStr.split("\\s+"));
            int tmp = 3;
            if (req.queryParams("limit") != null && req.queryParams("limit").length() > 0) {
                try {
                    tmp = Integer.valueOf(req.queryParams("limit"));
                } catch (Exception e) {
                }
            }
            final int tagLimit = tmp;

            // make sure patents exist
            // run model

            List<Pair<String, Double>> topTags = new ArrayList<>(function.apply(patents, tagLimit));

            long designCount = patents.stream().filter(p -> p.startsWith("D")).count();
            long plantCount = patents.stream().filter(p -> p.startsWith("PP")).count();
            if (designCount > 0) topTags.add(new Pair<>("Design", new Double(designCount) / patents.size()));
            if (plantCount > 0) topTags.add(new Pair<>("Plant", new Double(plantCount) / patents.size()));

            topTags = topTags.stream().sorted((p1, p2) -> Double.compare(p2.getSecond(), p1.getSecond())).limit(tagLimit).collect(Collectors.toList());

            // return results
            if (topTags.isEmpty())
                return new Gson().toJson(new SimpleAjaxMessage("Unable to predict any technologies"));
            return new Gson().toJson(topTags.stream().map(tag -> tag.getFirst()).collect(Collectors.toList()));
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error in request");
        }
    }


    public static void main(String[] args) throws Exception {
        port(4567);
        StartServer();
    }

    interface Function2<T1,T2,T3> {
        T3 apply(T1 t1, T2 t2);
    }
}
