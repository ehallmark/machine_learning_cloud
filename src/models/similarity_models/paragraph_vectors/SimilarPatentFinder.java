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
        boolean testing = false;
        try {
            if(testing==true) {
                paragraphVectors = ParagraphVectorModel.loadTestParagraphsModel();
            } else {
                paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
            }
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
        /*RelatedAssetsGraph relatedAssetsGraph = RelatedAssetsGraph.get();
        WeightLookupTable<VocabWord> lookupTable = getWeightLookupTable();
        Map<String,INDArray> toSave = Collections.synchronizedMap(new HashMap<>());
        Database.getAllPatentsAndApplications().parallelStream().forEach(patent->{
            int idx = relatedAssetsGraph.indexForAsset(patent);
            if(idx>=0) {
                INDArray vec = lookupTable.vector(String.valueOf(idx));
                if (vec != null) toSave.put(patent, vec);
            }
        });
        Database.getAssignees().parallelStream().forEach(assignee->{
            INDArray vec = lookupTable.vector(assignee);
            if(vec!=null) toSave.put(assignee,vec);
        });
        Database.trySaveObject(toSave,oldFile);*/
        Map<String,INDArray> toSave = (Map<String,INDArray>)Database.tryLoadObject(oldFile);
    // TODO clean up above this comment
        // create filing map
        Map<String,INDArray> filingMap = Collections.synchronizedMap(new HashMap<>());
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
        Map<String,Collection<String>> filingToAllAssetsMap = filingToAssetMap.getAllDataMap();
        filingToAllAssetsMap.entrySet().parallelStream().forEach(e->{
            Collection<INDArray> vectors = new ArrayList<>();
            e.getValue().forEach(asset->{
                if(toSave.containsKey(asset)) {
                    vectors.add(toSave.get(asset).get(NDArrayIndex.interval(0, SimilarityAttribute.vectorSize, false)));
                }
            });
            if(vectors.isEmpty()) return;
            INDArray avg = Nd4j.vstack(vectors).mean(0);
            avg.divi(avg.norm2Number());
            filingMap.put(e.getKey(), avg);
        });
        // assignees
        Database.getAssignees().parallelStream().forEach(assignee->{
            INDArray vec = toSave.get(assignee);
            if(vec!=null) filingMap.put(assignee,vec.dup().divi(vec.norm2Number()));
        });
        Database.trySaveObject(filingMap,file);
    }
}
