package user_interface.ui_models.engines;

import data_pipeline.helpers.CombinedModel;
import j2html.tags.Tag;
import models.similarity_models.combined_similarity_model.CombinedSimilarityModel;
import models.similarity_models.combined_similarity_model.CombinedSimilarityPipelineManager;
import models.similarity_models.combined_similarity_model.CombinedSimilarityVAEPipelineManager;
import models.similarity_models.combined_similarity_model.Word2VecToCPCIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.TEXT_TO_SEARCH_FOR;
import static user_interface.server.SimilarPatentServer.extractString;

/**
 * Created by ehallmark on 2/28/17.
 */
public class TextSimilarityEngine extends AbstractSimilarityEngine {
    private static ComputationGraph wordToEncodingNet;
    private static final int maxSampleLength = 15;
    private static final int maxNumSamples = 30;
    private static final double numDocs = 1000000;

    public TextSimilarityEngine() {
        loadSimilarityNetworks();
    }

    private void loadSimilarityNetworks() {
        synchronized (TextSimilarityEngine.class) {
            if (wordToEncodingNet == null) {
                String similarityModelName = CombinedSimilarityPipelineManager.MODEL_NAME_SMALL;
                CombinedSimilarityPipelineManager combinedSimilarityPipelineManager = new CombinedSimilarityPipelineManager(similarityModelName, null, null, null);
                combinedSimilarityPipelineManager.initModel(false);

                CombinedModel<ComputationGraph> combinedModel = (CombinedModel<ComputationGraph>) combinedSimilarityPipelineManager.getModel().getNet();
                wordToEncodingNet = combinedModel.getNameToNetworkMap().get(CombinedSimilarityModel.WORD_CPC_2_VEC);
            }
        }
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        // get input data
        return null;
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        final Random random = new Random(359);
        String text = extractString(req, TEXT_TO_SEARCH_FOR, "").toLowerCase().trim();

        System.out.println("Running text similarity model...");

        List<INDArray> featureVecs = new ArrayList<>(maxNumSamples);
        Word2Vec word2Vec = CombinedSimilarityVAEPipelineManager.getOrLoadManager().getWord2Vec();
        if(text.length()>0) {
            if(wordToEncodingNet==null) loadSimilarityNetworks();
            // get the input to the word to cpc network
            List<String> validWords = Stream.of(text.split("\\s+")).filter(word->!Constants.STOP_WORD_SET.contains(word)&&word2Vec.vocab().containsWord(word)).collect(Collectors.toList());
            System.out.println(" Num valid words found: "+validWords.size());
            if(validWords.size()>0) {
                for (int i = 0; i < Math.min(maxNumSamples,Math.max(1,validWords.size()-maxSampleLength)); i++) {
                    int wordLimit = Math.min(validWords.size(), maxSampleLength);
                    int start = validWords.size() > wordLimit ? random.nextInt(validWords.size() - wordLimit) : 0;
                    List<VocabWord> vocabWords = validWords.stream().skip(start).limit(wordLimit).flatMap(word -> {
                        VocabWord vocabWord = new VocabWord(1, word);
                        vocabWord.setSequencesCount(1);
                        vocabWord.setElementFrequency(1);
                        return Stream.of(vocabWord);
                    }).collect(Collectors.toList());

                    Map<String, Integer> wordCounts = Word2VecToCPCIterator.groupingBOWFunction.apply(vocabWords);
                    List<INDArray> wordVectors = wordCounts.entrySet().stream().map(e -> {
                        double tf = e.getValue();
                        double idf = Math.log(1d + (numDocs / Math.max(30, word2Vec.getVocab().docAppearedIn(e.getKey()))));
                        INDArray phraseVec = Word2VecToCPCIterator.getPhraseVector(word2Vec, e.getKey(), tf * idf);
                        if (phraseVec != null) {
                            return phraseVec;
                        }
                        return null;
                    }).filter(vec -> vec != null).collect(Collectors.toList());
                    if (wordVectors.isEmpty()) continue;

                    INDArray featureVec = Nd4j.vstack(wordVectors).mean(0);

                    if (featureVec != null && featureVec.sumNumber().doubleValue() > 0) {
                        featureVec = Transforms.unitVec(featureVec);
                        featureVecs.add(featureVec);
                    }
                }
            }
            if(featureVecs.size()>0) {
                // encode using word to cpc network
                avg = wordToEncodingNet.output(false, Transforms.unitVec(Nd4j.vstack(featureVecs).mean(0)))[0];
                avg.divi(avg.norm2Number());
            } else {
                throw new RuntimeException("Unable to find similarity from provided text.");
            }
            System.out.println("Num features found: "+featureVecs.size());
        }
    }
    @Override
    public String getId() {
        return TEXT_TO_SEARCH_FOR;
    }

    @Override
    public String getName() {
        return Constants.TEXT_SIMILARITY;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","Enter any text or document").withId(getId()).withName(getId())
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        return new TextSimilarityEngine();
    }
}
