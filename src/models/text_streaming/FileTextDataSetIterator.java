package models.text_streaming;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 11/19/2017.
 */
public class FileTextDataSetIterator implements LabelAwareIterator {
    private static File BASE_DIR = new File("asset_text_data/");
    static File trainFile = new File(BASE_DIR, "train_data.csv");
    static File devFile1 = new File(BASE_DIR, "dev1_data.csv");
    static File devFile2 = new File(BASE_DIR, "dev2_data.csv");
    static File devFile3 = new File(BASE_DIR, "dev3_data.csv");
    static File devFile4 = new File(BASE_DIR, "dev4_data.csv");
    static File testFile = new File(BASE_DIR, "test_data.csv");
    private static Map<Type,File> typeToFileMap = Collections.synchronizedMap(new HashMap<>());

    public enum Type {
        TRAIN, DEV1, DEV2, DEV3, DEV4, TEST
    }

    static {
        if(!BASE_DIR.exists()) {
            BASE_DIR.mkdirs();
        }
        typeToFileMap.put(Type.TRAIN,trainFile);
        typeToFileMap.put(Type.DEV1,devFile1);
        typeToFileMap.put(Type.DEV2,devFile2);
        typeToFileMap.put(Type.DEV3,devFile3);
        typeToFileMap.put(Type.DEV4,devFile4);
        typeToFileMap.put(Type.TEST,testFile);
    }


    static Map<Type,File> getTypeMap() {
        return typeToFileMap;
    }

    private File dataFile;
    private LineIterator lineIterator;
    public FileTextDataSetIterator(Type type) {
        dataFile = typeToFileMap.get(type);
        reset();
    }


    @Override
    public LabelledDocument nextDocument() {
        return next();
    }

    @Override
    public boolean hasNextDocument() {
        return lineIterator.hasNext();
    }

    @Override
    public boolean hasNext() {
        return lineIterator.hasNext();
    }

    @Override
    public void reset() {
        if(this.lineIterator!=null) {
            shutdown();
        }
        try {
            this.lineIterator = FileUtils.lineIterator(dataFile);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public LabelsSource getLabelsSource() {
        return null;
    }

    @Override
    public void shutdown() {
        LineIterator.closeQuietly(this.lineIterator);
    }

    @Override
    public LabelledDocument next() {
        String line = lineIterator.nextLine();
        String[] data = line.split(",",2);
        String label = data[0];
        String text = data[1];
        LabelledDocument doc = new LabelledDocument();
        doc.setContent(text);
        doc.setLabels(Collections.singletonList(label));
        return doc;
    }
}
