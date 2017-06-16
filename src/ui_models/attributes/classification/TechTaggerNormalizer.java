package ui_models.attributes.classification;

import genetics.GeneticAlgorithm;
import genetics.Listener;
import genetics.SolutionCreator;
import seeding.Constants;
import seeding.Database;
import similarity_models.paragraph_vectors.WordFrequencyPair;
import org.deeplearning4j.berkeley.Pair;
import tools.MinHeap;
import ui_models.attributes.classification.genetics.TechTaggerSolution;
import ui_models.attributes.classification.genetics.TechTaggerSolutionCreator;

import java.io.File;
import java.util.*;

/**
 * Created by Evan on 3/4/2017.
 */
public class TechTaggerNormalizer implements ClassificationAttr {
    private List<Pair<ClassificationAttr,Double>> taggerPairs;
    private List<ClassificationAttr> taggers;
    private List<Double> weights;
    private static List<Double> DEFAULT_WEIGHTS;
    private static final File weightsFile = new File("tech_tagger_normalizer_weights.jobj");
    private static final int timeLimit = 10*60*1000;

    public String getName() {
        return Constants.TECHNOLOGY;
    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        System.out.println("Starting genetic algorithm...");
        SolutionCreator creator = new TechTaggerSolutionCreator(validationData, taggers);
        Listener listener = null;// new SVMSolutionListener();
        GeneticAlgorithm<TechTaggerSolution> algorithm = new GeneticAlgorithm<>(creator,30,listener,20);
        algorithm.simulate(timeLimit,0.5,0.5);
        TechTaggerSolution bestSolution = algorithm.getBestSolution();
        return new TechTaggerNormalizer(bestSolution.getTaggers(),bestSolution.getWeights());
    }

    @Override
    public void save() {
        Database.trySaveObject(weights,weightsFile);
    }

    @Override
    public ClassificationAttr untrainedDuplicate() {
        return new TechTaggerNormalizer(taggers, weights);
    }


    @Override
    public void train(Map<String, Collection<String>> trainingData) {
        // Do nothing

    }

    private static ClassificationAttr tagger;

    public TechTaggerNormalizer(List<ClassificationAttr> taggers, List<Double> weights) {
        if(taggers.size()!=weights.size()) throw new RuntimeException("Illegal arguments in techtaggernormalizer");
        this.weights=weights;
        this.taggers=taggers;
        this.taggerPairs=new ArrayList<>(taggers.size());
        for(int i = 0; i < taggers.size(); i++) {
            this.taggerPairs.add(new Pair<>(taggers.get(i),weights.get(i)));
        }
    }

    public int numClassifications() {
        return taggerPairs.stream().min(Comparator.comparingInt(t->t.getFirst().numClassifications())).get().getFirst().numClassifications();
    }

    public Collection<String> getClassifications() { return taggerPairs.stream().map(tagger->tagger.getFirst().getClassifications()).reduce((c1,c2)->{
            Set<String> c3 = new HashSet<>(c1);
            c3.addAll(c2);
            return c3;
        }).get();
    }

    public static ClassificationAttr getDefaultTechTagger() {
        if(tagger==null) {
            tagger=new TechTaggerNormalizer(TechTaggerSolutionCreator.getTaggers(),getWeights());
        }
        return tagger;
    }

    private static List<Double> getWeights() {
        if(DEFAULT_WEIGHTS==null) {
            DEFAULT_WEIGHTS=loadWeights();
        }
        return DEFAULT_WEIGHTS;
    }

    private static List<Double> loadWeights() {
        if(weightsFile.exists()) {
            return (List<Double>) Database.tryLoadObject(weightsFile);
        } else {
            List<Double> weights = new ArrayList<>();
            for(int i = 0; i < TechTaggerSolutionCreator.getTaggers().size(); i++) {
                weights.add(1d);
            }
            return weights;
        }
    }

    private List<Pair<String,Double>> technologyHelper(Collection<String> portfolio, int n) {
        Map<String,Double> technologyScores = new HashMap<>();
        taggerPairs.forEach(taggerPair->{
            ClassificationAttr tagger = taggerPair.getFirst();
            double weight = taggerPair.getSecond();
            List<Pair<String, Double>> data = tagger.attributesFor(portfolio,n);
            if (data != null && data.size() >= 1) {
                for (Pair<String, Double> pair : data) {
                    double val = pair.getSecond() * weight;
                    if (technologyScores.containsKey(pair.getFirst())) {
                        technologyScores.put(pair.getFirst(), Math.max(technologyScores.get(pair.getFirst()), val));
                    } else {
                        technologyScores.put(pair.getFirst(), val);
                    }
                }
            }
        });
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(n);
        technologyScores.entrySet().forEach(e->{
            String name = e.getKey();
            double val = e.getValue();
            heap.add(new WordFrequencyPair<>(name, val));
        });
        List<Pair<String,Double>> data = new ArrayList<>(n);
        while(!heap.isEmpty()) {
            WordFrequencyPair<String,Double> pair = heap.remove();
            data.add(0,new Pair<>(pair.getFirst(),pair.getSecond()));
        }
        return data;
    }

    @Override
    public List<Pair<String, Double>> attributesFor(Collection<String> portfolio, int n) {
        return technologyHelper(portfolio,n);
    }


}
