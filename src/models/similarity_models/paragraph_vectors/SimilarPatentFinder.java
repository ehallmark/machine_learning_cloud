package models.similarity_models.paragraph_vectors;


import models.dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import seeding.ai_db_updater.tools.RelatedAssetsGraph;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import models.similarity_models.BaseSimilarityModel;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.attributes.script_attributes.SimilarityAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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
            paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
        } catch(Exception e) {
            e.printStackTrace();
        }
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
        //ParagraphVectorModel.main(args);

        System.out.println("Finished running pvector model...");
        WeightLookupTable<VocabWord> lookup = getWeightLookupTable();
        // create filing map
        Map<String,INDArray> filingMap = Collections.synchronizedMap(new HashMap<>());
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
        Collection<String> allFilings = new HashSet<>();
        allFilings.addAll(filingToAssetMap.getPatentDataMap().keySet());
        allFilings.addAll(filingToAssetMap.getApplicationDataMap().keySet());
        allFilings.addAll(Database.getAssignees());
        AtomicLong missing = new AtomicLong(0);
        allFilings.parallelStream().forEach(filing->{
            INDArray vec = lookup.vector(filing);
            if(vec!=null) {
                vec.divi(vec.norm2Number());
                filingMap.put(filing, vec);
            } else {
                missing.getAndIncrement();
                System.out.println("Missing: "+missing.get());
            }
        });
        System.out.println("Num vectors: "+filingMap.size());
        Database.trySaveObject(filingMap,file);
    }
}
