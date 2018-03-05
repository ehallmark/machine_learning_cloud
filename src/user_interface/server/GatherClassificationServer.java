package user_interface.server;

import com.google.gson.Gson;
import elasticsearch.DataIngester;
import elasticsearch.MyClient;
import models.classification_models.TechnologyClassifier;
import org.nd4j.linalg.primitives.PairBackup;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import seeding.Database;
import user_interface.server.tools.SimpleAjaxMessage;
import spark.Request;
import spark.Response;
import models.classification_models.ClassificationAttr;
import models.classification_models.WIPOTechnologyClassifier;
import user_interface.ui_models.attributes.computable_attributes.TechnologyAttribute;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static spark.Spark.*;

/**
 * Created by ehallmark on 11/1/16.
 */
public class GatherClassificationServer {
    private static ClassificationAttr techTagger = new TechnologyClassifier(TechnologyAttribute.getOrCreate());
    private static ClassificationAttr wipoTagger = new WIPOTechnologyClassifier();
    public static void StartServer() throws Exception {
        get("/predict_patents", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> techTagger.attributesFor(patents,tagLimit)));
        post("/predict_patents", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> techTagger.attributesFor(patents,tagLimit)));

        post("/predict_wipo", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> wipoTagger.attributesFor(patents, tagLimit)));
        get("/predict_wipo", (req, res) -> handleRequest(req, res, (patents, tagLimit) -> wipoTagger.attributesFor(patents, tagLimit)));
    }


    private static String handleRequest(Request req, Response res, Function2<Collection<String>,Integer,List<PairBackup<String,Double>>> function) throws Exception {
        try {
            res.type("application/json");
            if (req.queryParams("reelFrames") == null || req.queryParams("reelFrames").length() == 0)
                return new Gson().toJson(new SimpleAjaxMessage("Please provide at least one patent."));

            List<String> reelFrames = Arrays.asList(req.queryParams("reelFrames").split("\\s+"));
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

            TransportClient client = MyClient.get();
            System.out.println("Searching for reel frames: " + String.join("; ", reelFrames));

            Collection<String> patents = Stream.of(client.prepareSearch(DataIngester.INDEX_NAME)
                    .setTypes(DataIngester.TYPE_NAME)
                    .setFetchSource(false)
                    .setSize(100)
                    .setQuery(QueryBuilders.termsQuery(Constants.REEL_FRAME, reelFrames))
                    .get().getHits().getHits()).map(hit -> hit.getId()).collect(Collectors.toList());
            System.out.println("Found " + patents.size() + " patents");

            List<PairBackup<String, Double>> topTags = new ArrayList<>(function.apply(patents, tagLimit));

            long designCount = patents.stream().filter(p -> p.startsWith("D")).count();
            long plantCount = patents.stream().filter(p -> p.startsWith("PP")).count();
            if (designCount > 0) topTags.add(new PairBackup<>("Design", new Double(designCount) / patents.size()));
            if (plantCount > 0) topTags.add(new PairBackup<>("Plant", new Double(plantCount) / patents.size()));

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
        Database.initializeDatabase();
        StartServer();
    }

    interface Function2<T1,T2,T3> {
        T3 apply(T1 t1, T2 t2);
    }
}
