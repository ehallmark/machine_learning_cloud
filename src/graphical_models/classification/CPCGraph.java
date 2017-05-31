package graphical_models.classification;

import model.functions.inference_methods.BeliefPropagation;
import model.functions.inference_methods.InferenceMethod;
import model.graphs.MarkovNet;
import model.learning.algorithms.MarkovLearningAlgorithm;
import model.nodes.Node;
import seeding.Database;

import java.util.*;

/**
 * Created by Evan on 5/31/2017.
 */
public class CPCGraph {

    public static void main(String[] args) {
        Database.initializeDatabase();
        Collection<String> classifications = Database.getClassCodes();
        Map<String,Set<String>> patentToClassificationMap = Database.getPatentToClassificationMap();

        System.out.println("Adding cpc nodes");
        MarkovNet net = new MarkovNet();
        classifications.forEach(cpc->{
            net.addBinaryNode(cpc);
        });

        System.out.println("Adding and connecting patent nodes");
        patentToClassificationMap.forEach((patent,cpcs)->{
            Node patentNode = net.addBinaryNode(patent);
            cpcs.forEach(cpc->{
                Node cpcNode = net.findNode(cpc);
                net.connectNodes(patentNode,cpcNode);
                net.addFactorNode(null,patentNode,cpcNode);
            });
        });

        Map<String,Integer> mapCopy = new HashMap<>();
        classifications.forEach(cpc->mapCopy.put(cpc,0));

        System.out.println("Creating data");
        List<Map<String,Integer>> data = new ArrayList<>(patentToClassificationMap.size());
        patentToClassificationMap.forEach((patent,cpcs)->{
            Map<String,Integer> point = new HashMap<>(mapCopy);
            point.put(patent,1);
            cpcs.forEach(cpc->{
                point.put(cpc,1);
            });
            data.add(point);
        });

        System.out.println("Starting training");
        net.setTrainingData(data);
        net.applyLearningAlgorithm(new MarkovLearningAlgorithm(net,50d, new BeliefPropagation()),50);
    }
}
