package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.models.Model;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Collection;

/**
 * Created by ehallmark on 9/12/17.
 */
public abstract class Stage<V> {
    private static final File baseDir = new File(Constants.DATA_FOLDER+"technologyPredictionStages/");
    protected File mainDir;
    protected int year;
    protected V data;
    public Stage(Model model, int year) {
        this.year=year;
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException(baseDir.getAbsolutePath()+" must be a directory.");
        this.mainDir = new File(baseDir, model.getModelName());
        if(!mainDir.exists()) mainDir.mkdir();
        if(!mainDir.isDirectory()) throw new RuntimeException(mainDir.getAbsolutePath()+" must be a directory.");
    }
    protected void loadData() {
        data = (V) Database.tryLoadObject(getFile());
    }
    public V get() {
        return data;
    }
    public File getFile() {
        return new File(mainDir,this.getClass().getSimpleName()+year);
    }

    public abstract V run(boolean run);
}
