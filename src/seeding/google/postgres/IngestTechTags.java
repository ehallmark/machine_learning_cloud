package seeding.google.postgres;

import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.keyphrase_prediction.MultiStem;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class IngestTechTags {

    public static void main(String[] args) throws Exception {
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 10;

        WordCPC2VecPipelineManager encodingPipelineManager = new WordCPC2VecPipelineManager(WordCPC2VecPipelineManager.DEEP_MODEL_NAME,-1,-1,-1);
        encodingPipelineManager.runPipeline(false,false,false,false,-1,false);
        KeyphrasePredictionPipelineManager pipelineManager = new KeyphrasePredictionPipelineManager(encodingPipelineManager);

        pipelineManager.initStages(true,true,false,false);

        System.out.println("Num multistem vectors: "+pipelineManager.buildKeywordToLookupTableMap().size());

        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);


        List<MultiStem> keywords = Collections.synchronizedList(new ArrayList<>(pipelineManager.buildKeywordToLookupTableMap().keySet()));

        //CPCHierarchy hierarchy = CPCHierarchy.get();
        Map<String,INDArray> cpcPredictions = encodingPipelineManager.getOrLoadCPCVectors();

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("insert into big_query_technologies_helper (code,technology) values (?,?) on conflict (code) do update set technology=?");
        ReentrantLock lock = new ReentrantLock();
        AtomicInteger i = new AtomicInteger(0);
        Consumer<Pair<String,Set<String>>> consumer = pair -> {
            String technology = pair.getSecond().stream().findAny().orElse(null);
            if(technology==null) return;
            lock.lock();
            try {
                ps.setString(1,pair.getFirst());
                ps.setString(2,technology);
                ps.setString(3,technology);
                ps.executeUpdate();
                if(i.getAndIncrement()%1000==999) Database.commit();
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            } finally {
                lock.unlock();
            }
        };

        pipelineManager.predict(keywords,cpcPredictions,1,0.25,consumer);

        Database.commit();
        Database.close();

    }
}
