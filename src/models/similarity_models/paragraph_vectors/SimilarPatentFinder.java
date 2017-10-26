package models.similarity_models.paragraph_vectors;


import elasticsearch.DataSearcher;
import elasticsearch.MyClient;
import models.dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import models.dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import models.similarity_models.BaseSimilarityModel;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder extends BaseSimilarityModel {
    private static final File oldFile = new File("data/similar_patent_finder_lookup_table.jobj");
    private static final File file = new File("data/similar_filing_finder_lookup_table.jobj");
    private static Map<String,INDArray> LOOKUP_TABLE;
    protected static ParagraphVectors paragraphVectors;

    public static WeightLookupTable<VocabWord> getWeightLookupTable() {
        if(paragraphVectors==null) loadLookupTable();
        return paragraphVectors.getLookupTable();
    }

    private static void loadLookupTable() {
        if(paragraphVectors!=null)return;
        try {
            paragraphVectors =  ParagraphVectorModel.getNet();

            if(paragraphVectors==null) {
                paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
            } else {
                System.out.println("Using existing trained model...");
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        System.out.println("Num vectors: "+LOOKUP_TABLE.size());
        Database.trySaveObject(LOOKUP_TABLE,file);
    }

    public SimilarPatentFinder(Collection<String> candidateSet) {
        super(candidateSet.stream().map(str->new Item(str)).collect(Collectors.toList()), getLookupTable());
    }

    public synchronized static Map<String,INDArray> getLookupTable() {
        if(LOOKUP_TABLE==null) {
            LOOKUP_TABLE=(Map<String,INDArray>)Database.tryLoadObject(file);
        }
        return LOOKUP_TABLE;
    }
    public static void main(String[] args) {
        // first run pvector model
        ParagraphVectorModel.main(args);

        System.out.println("Finished running pvector model...");
        WeightLookupTable<VocabWord> lookup = getWeightLookupTable();
        // create filing map
        LOOKUP_TABLE = Collections.synchronizedMap(new HashMap<>());
        Collection<String> allFilings = new HashSet<>();
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
        allFilings.addAll(filingToAssetMap.getPatentDataMap().keySet());
        allFilings.addAll(filingToAssetMap.getApplicationDataMap().keySet());
        allFilings.addAll(Database.getAssignees());
        AtomicLong missing = new AtomicLong(0);
        AtomicLong seen = new AtomicLong(0);
        allFilings.parallelStream().forEach(asset->{
            INDArray vec = lookup.vector(asset);
            seen.getAndIncrement();
            if(vec!=null) {
                vec.divi(vec.norm2Number());
                LOOKUP_TABLE.put(asset, vec);
            } else {
                missing.getAndIncrement();
                if(seen.get()%100000==99999) {
                    System.out.println("Missing %: " + ((missing.get() * 100) / seen.get())+"%");
                }
            }
        });
        save();
    }

    public static void updateLatest(Collection<String> newAssets) throws IOException {
        ParagraphVectors model = ParagraphVectorModel.loadParagraphsModel();
        Map<String,INDArray> vectorMap = SimilarPatentFinder.getLookupTable();
        // get paragraphs for new assets
        Function<SearchHit,Item> transformer = searchHit -> {
            Sequence<VocabWord> sequence = DatabaseIteratorFactory.extractSequence(searchHit);
            INDArray vec = model.inferVector(sequence.getElements());
            if(vec!=null) {
                vec = vec.div(vec.norm2Number());
                vectorMap.put(sequence.getSequenceLabel().getLabel(),vec);
            }
            return null;
        };

        SearchRequestBuilder searchRequest = DatabaseIteratorFactory.getRequestBuilder(MyClient.get());
        searchRequest = searchRequest.setQuery(QueryBuilders.idsQuery().addIds(newAssets.toArray(new String[]{})));
        DataSearcher.iterateOverSearchResults(searchRequest.get(), transformer, -1, false);
        // save
        SimilarPatentFinder.save();
    }
}
