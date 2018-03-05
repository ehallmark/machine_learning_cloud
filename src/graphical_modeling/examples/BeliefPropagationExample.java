package graphical_modeling.examples;

import graphical_modeling.model.functions.heuristic.MinimalCliqueSizeHeuristic;
import graphical_modeling.model.functions.inference_methods.BeliefPropagation;
import graphical_modeling.model.graphs.*;
import graphical_modeling.model.learning.algorithms.ExpectationMaximizationAlgorithm;
import graphical_modeling.model.nodes.FactorNode;
import graphical_modeling.model.nodes.Node;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/26/17.
 */
public class BeliefPropagationExample {
    public static void main(String[] args) {
        // Create Bayesian Network
        BayesianNet bayesianNet = new BayesianNet();

        // Add nodes
        Node n1 = bayesianNet.addBinaryNode("Node 1");
        Node n2 = bayesianNet.addBinaryNode("Node 2");
        Node n3 = bayesianNet.addBinaryNode("Node 3");
        Node n4 = bayesianNet.addBinaryNode("Node 4");
        Node n5 = bayesianNet.addBinaryNode("Node 5"); // Shorthand
        Node n6 = bayesianNet.addBinaryNode("Node 6"); // Shorthand

        // Connect nodes
        bayesianNet.connectNodes("Node 1","Node 2");
        bayesianNet.connectNodes("Node 2","Node 3");
        bayesianNet.connectNodes("Node 2","Node 4");
        bayesianNet.connectNodes("Node 3","Node 4");
        bayesianNet.connectNodes("Node 3","Node 5");
        bayesianNet.connectNodes(n6,n5); // Or by variable


        /*
        // Add known factors
        bayesianNet.addFactorNode(new float[]{1,2,3,4},n1,n2);
        bayesianNet.addFactorNode(new float[]{5,6,7,8},n2,n3);
        bayesianNet.addFactorNode(new float[]{1,2,3,5},n2,n4);
        bayesianNet.addFactorNode(new float[]{7,11,3,5},n3,n4);
        bayesianNet.addFactorNode(new float[]{4,3,2,1},n3,n5);
        bayesianNet.addFactorNode(new float[]{6,3,12,9},n6,n5);
        */

        // Add factors to learn
        bayesianNet.addFactorNode(null,n1,n2);
        bayesianNet.addFactorNode(null,n2,n3);
        bayesianNet.addFactorNode(null,n2,n4);
        bayesianNet.addFactorNode(null,n3,n4);
        bayesianNet.addFactorNode(null,n3,n5);
        bayesianNet.addFactorNode(null,n6,n5);
        bayesianNet.addFactorNode(null,n1);
        bayesianNet.addFactorNode(null,n6);

        Random rand = new Random(69);
        List<Map<String,Integer>> assignments = new ArrayList<>();
        Map<String,Integer> assignment = new HashMap<>();
        for(int i = 0; i < 1000; i++) {
            if(i%10!=0) {
                for(Node node : bayesianNet.getAllNodesList()) {
                    // randomly don't include some
                    if(rand.nextBoolean()||rand.nextBoolean()) {
                        assignment.put(node.getLabel(),rand.nextInt(node.getCardinality()));
                    }
                }
            } else {
                if(assignment.size()>0)assignments.add(assignment);
                assignment=new HashMap<>();
            }

        }

        // Moralize to a Markov Network
        MarkovNet markovNet = bayesianNet.moralize();
        markovNet.setTrainingData(assignments);
        // Triangulate with given heuristic
        markovNet.triangulateInPlace(new MinimalCliqueSizeHeuristic());
        markovNet.applyLearningAlgorithm(new ExpectationMaximizationAlgorithm(markovNet,10, new BeliefPropagation()),1000);

        // Also try on Bayesian net just for fun
        bayesianNet.setTrainingData(assignments);
        bayesianNet.applyLearningAlgorithm(new ExpectationMaximizationAlgorithm(bayesianNet,10, new BeliefPropagation()),100);
        MarkovNet markovNet2 = bayesianNet.moralize();
        markovNet2.triangulateInPlace(new MinimalCliqueSizeHeuristic());

        // Create Clique Tree From Triangulated Graph
        CliqueTree cliqueTree = markovNet.createCliqueTree();
        CliqueTree cliqueTree2 = markovNet2.createCliqueTree();

        // Add assignment
        Map<String,Integer> test = new HashMap<>();
        //test.put("Node 3",0);
        cliqueTree.setCurrentAssignment(test);
        cliqueTree2.setCurrentAssignment(test);

        // Run Belief Propagation
        Map<String,FactorNode> result = cliqueTree.runBeliefPropagation(bayesianNet.getAllNodesList().stream().map(node->node.getLabel()).collect(Collectors.toList()));
        Map<String,FactorNode> result2 = cliqueTree2.runBeliefPropagation(bayesianNet.getAllNodesList().stream().map(node->node.getLabel()).collect(Collectors.toList()));

        System.out.println("Result 1: ");
        result.forEach((label,f)->{
            System.out.println("Prob "+label+": "+f);
        });

        System.out.println("Result 2: ");
        result2.forEach((label,f)->{
            System.out.println("Prob "+label+": "+f);
        });

        // Gibbs Chain
        System.out.println("Gibbs Chain: ");
        Iterator<Map<String,FactorNode>> chain = new GibbsChain(bayesianNet,test);
        int chainLength = 1000;
        for(int i = 0; i < chainLength; i++) {
            Map<String,FactorNode> p = chain.next();
            if(i==chainLength-1)p.forEach((label,f)->  System.out.println("Prob "+label+": "+f));
        }

        // MH chain
        System.out.println("MH Chain: ");
        chain = new MetropolisHastingsChain(bayesianNet,test);
        for(int i = 0; i < chainLength; i++) {
            Map<String,FactorNode> p = chain.next();
            if(i==chainLength-1)p.forEach((label,f)->  System.out.println("Prob "+label+": "+f));
        }
    }
}
