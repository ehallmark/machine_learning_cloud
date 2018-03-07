package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import lombok.Setter;
import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.keyphrase_prediction.stages.Stage1;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public abstract class AbstractWordCPC2VecPipelineManager<T extends SequenceIterator<VocabWord>> extends DefaultPipelineManager<T,Map<String,INDArray>> {

    protected Map<String,Collection<CPC>> cpcMap;
    protected CPCHierarchy hierarchy;
    @Getter
    protected int numEpochs;
    @Getter
    protected List<String> testWords;
    @Getter @Setter
    protected int maxSamples;

    public AbstractWordCPC2VecPipelineManager(File inputFolder, File predictionsFile, int numEpochs, int maxSamples) {
        super(inputFolder,predictionsFile);
        this.numEpochs=numEpochs;
        this.maxSamples=maxSamples;
        this.testWords = Arrays.asList("A","B","C","D","E","F","G","A02","BO3Q","Y","C07F","A02A1/00","semiconductor","computer","internet","virtual","intelligence","artificial","chemistry","biology","electricity","agriculture","automobile","robot");
    }

    @Override
    public void rebuildPrerequisiteData() {
        try {
            //System.out.println("Starting to pull latest text data from elasticsearch...");
            //ESTextDataSetIterator.main(null);
            System.out.println("Starting to build vocab map...");
            Stage1 stage1 = new Stage1(KeyphrasePredictionPipelineManager.modelParams);
            stage1.run(true);

        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public abstract Map<String,INDArray> getOrLoadWordVectors();
    public abstract Map<String,INDArray> getOrLoadCPCVectors();


    public synchronized CPCHierarchy getHierarchy() {
        if(hierarchy==null) {
            hierarchy = new CPCHierarchy();
            hierarchy.loadGraph();
        }
        return hierarchy;
    }

    public synchronized Map<String,Collection<CPC>> getCPCMap() {
        if(cpcMap==null) {

            //cpcMap = (Map<String,Collection<CPC>>) Database.tryLoadObject(cpcMapFile);

            if(cpcMap == null) {
                Map<String, String> patentToFiling = new AssetToFilingMap().getPatentDataMap();
                Map<String, String> appToFiling = new AssetToFilingMap().getApplicationDataMap();
                getHierarchy();
                Map<String, Set<String>> assetToCPCStringMap = new HashMap<>();
                new AssetToCPCMap().getApplicationDataMap().entrySet().forEach(e -> {
                    assetToCPCStringMap.put(appToFiling.get(e.getKey()), e.getValue());
                });
                new AssetToCPCMap().getPatentDataMap().entrySet().forEach(e -> {
                    assetToCPCStringMap.put(patentToFiling.get(e.getKey()), e.getValue());
                });
                cpcMap = assetToCPCStringMap.entrySet().parallelStream()
                        .filter(e -> assetToCPCStringMap.containsKey(e.getKey()))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().stream().map(label -> hierarchy.getLabelToCPCMap().get(ClassCodeHandler.convertToLabelFormat(label)))
                                .filter(cpc -> cpc != null)
                                .flatMap(cpc -> hierarchy.cpcWithAncestors(cpc).stream())
                                .distinct()
                                .collect(Collectors.toSet())))
                        .entrySet().parallelStream()
                        .filter(e -> e.getValue().size() > 0)
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

                //Database.trySaveObject(cpcMap,cpcMapFile);
            }

        }
        return cpcMap;
    }

    @Override
    protected void splitData() {
        // purposefully do nothing
    }


    @Override
    public DataSetManager<T> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
            File baseDir = FileTextDataSetIterator.BASE_DIR;
            File trainFile = new File(baseDir, FileTextDataSetIterator.trainFile.getName());
            File testFile = new File(baseDir, FileTextDataSetIterator.testFile.getName());
            File devFile = new File(baseDir, FileTextDataSetIterator.devFile2.getName());

            FileTextDataSetIterator trainIter = new FileTextDataSetIterator(trainFile);
            FileTextDataSetIterator testIter = new FileTextDataSetIterator(testFile);
            FileTextDataSetIterator devIter = new FileTextDataSetIterator(devFile);

            boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
            System.out.println("Using full text: "+fullText);
            datasetManager = new NoSaveDataSetManager<>(
                    getSequenceIterator(trainIter,numEpochs),
                    getSequenceIterator(testIter,1),
                    getSequenceIterator(devIter,1)
            );
        }
    }

    protected abstract T getSequenceIterator(FileTextDataSetIterator iterator, int nEpochs);


}
