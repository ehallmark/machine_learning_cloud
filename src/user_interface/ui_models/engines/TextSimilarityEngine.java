package user_interface.ui_models.engines;

import data_pipeline.helpers.CombinedModel;
import j2html.tags.Tag;
import lombok.Getter;
import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.similarity_models.combined_similarity_model.CombinedSimilarityModel;
import models.similarity_models.combined_similarity_model.CombinedSimilarityPipelineManager;
import models.similarity_models.combined_similarity_model.CombinedSimilarityVAEPipelineManager;
import models.similarity_models.combined_similarity_model.Word2VecToCPCIterator;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.similarity_models.word_to_cpc.word_to_cpc_encoding_model.WordToCPCEncodingNN;
import models.similarity_models.word_to_cpc.word_to_cpc_encoding_model.WordToCPCPipelineManager;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.concurrent.RecursiveTask;
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
    private static MultiLayerNetwork wordToEncodingNet;
    private static final int maxSampleLength = 15;
    private static final int maxNumSamples = 30;
    private static final long numDocs = 18000000L;

    private synchronized void loadSimilarityNetworks() {
        if(wordToEncodingNet==null) {
            String similarityModelName = CombinedSimilarityPipelineManager.MODEL_NAME;
            CombinedSimilarityPipelineManager combinedSimilarityPipelineManager = new CombinedSimilarityPipelineManager(similarityModelName, null, null, null);
            combinedSimilarityPipelineManager.initModel(false);

            CombinedModel<MultiLayerNetwork> combinedModel = (CombinedModel<MultiLayerNetwork>) combinedSimilarityPipelineManager.getModel().getNet();
            wordToEncodingNet = combinedModel.getNameToNetworkMap().get(CombinedSimilarityModel.WORD_CPC_2_VEC);
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

        List<VocabWord> vocabWords;
        List<INDArray> featureVecs = new ArrayList<>(maxNumSamples);
        Word2Vec word2Vec = CombinedSimilarityVAEPipelineManager.getOrLoadManager().getWord2Vec();
        if(text.length()>0) {
            if(wordToEncodingNet==null) loadSimilarityNetworks();
            // get the input to the word to cpc network
            String[] words = WordCPCIterator.defaultWordListFunction.apply(text);
            for(int i = 0; i < maxNumSamples; i++) {
                int wordLimit = Math.min(words.length, maxSampleLength);
                int start = words.length > wordLimit ? random.nextInt(words.length - wordLimit) : 0;
                vocabWords = Stream.of(text).filter(word -> !Constants.STOP_WORD_SET.contains(word)).skip(start).limit(wordLimit).flatMap(word -> {
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
            // encode using word to cpc network
            avg = wordToEncodingNet.activateSelectedLayers(0, wordToEncodingNet.getnLayers() - 1, Transforms.unitVec(Nd4j.vstack(featureVecs).mean(0)));
            avg.divi(avg.norm2Number());

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
