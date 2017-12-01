package user_interface.server;

import assignee_normalization.human_name_prediction.HumanNamePredictionModel;
import assignee_normalization.human_name_prediction.HumanNamePredictionPipelineManager;
import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import spark.Request;
import spark.Response;

import java.util.Map;

import static spark.Spark.*;

/**
 * Created by ehallmark on 12/1/17.
 */
public class HumanNamePredictionServer {

    public static void main(String[] args) throws Exception {
        DefaultPipelineManager.setLoggingLevel(Level.INFO);

        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;
        String modelName = HumanNamePredictionPipelineManager.MODEL_NAME;

        HumanNamePredictionPipelineManager pipelineManager = new HumanNamePredictionPipelineManager(modelName);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);

        HumanNamePredictionModel model = (HumanNamePredictionModel)pipelineManager.getModel();

        port(6969);
        post("/predict", (req,res)->{
            return handleResults(req,res,model);
        });
        get("/predict", (req,res)->{
            return handleResults(req,res,model);
        });
    }

    private synchronized static Object handleResults(Request req, Response res, HumanNamePredictionModel model) {
        String name = req.queryParamOrDefault("name","");
        if(name.length()>0) {
            Map<String,Boolean> map = model.isHuman(model.getNet(),name);
            if(map.containsKey(name)) {
                return map.get(name);
            }
        }
        return null;
    }
}
