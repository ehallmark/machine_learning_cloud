package models.text_streaming;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by Evan on 11/19/2017.
 */
public class FileTextDataSetIterator implements LabelAwareIterator {
    public static final File BASE_DIR = new File("abstract_text_data/");
    public static final File trainFile = new File(BASE_DIR, "train_data.csv");
    public static File devFile1 = new File(BASE_DIR, "dev1_data.csv");
    public static File devFile2 = new File(BASE_DIR, "dev2_data.csv");
    public static File devFile3 = new File(BASE_DIR, "dev3_data.csv");
    public static File devFile4 = new File(BASE_DIR, "dev4_data.csv");
    public static File testFile = new File(BASE_DIR, "test_data.csv");
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
    private boolean containsDate;
    private LocalDate currentDate;
    public FileTextDataSetIterator(Type type) {
        this(typeToFileMap.get(type));
    }
    public FileTextDataSetIterator(File file) {
        dataFile = file;
        this.containsDate = file.getParentFile().getName().contains("_date_");
        System.out.println("File text data iterator acknowledging dates: "+containsDate);
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
        currentDate = null;
        System.out.println("Resetting file text dataset iterator...");
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
        String[] data;
        List<String> labels = new ArrayList<>();
        String text;
        if(containsDate) {
            data = line.split(",", 3);
            String label = data[0];
            String date = data[1];
            text = data[2];
            labels.add(label);
            labels.add(date);
            currentDate = LocalDate.parse(date,DateTimeFormatter.ISO_DATE);
        } else {
            data = line.split(",", 2);
            String label = data[0];
            text = data[1];
            labels.add(label);
        }
        LabelledDocument doc = new LabelledDocument();
        doc.setContent(text);
        doc.setLabels(labels);
        return doc;
    }

    public LocalDate getCurrentDate() {
        return currentDate;
    }

    public static void transformData(File newBaseDir, Function<LabelledDocument,String> transformFunction, boolean write, int maxSamples) {
        if(!newBaseDir.exists()) newBaseDir.mkdirs();
        Stream.of(Type.TEST,Type.DEV1,Type.DEV2,Type.DEV3,Type.DEV4,Type.TRAIN).forEach(type->{
            File file = typeToFileMap.get(type);
            FileTextDataSetIterator iterator = new FileTextDataSetIterator(type);
            File newFile = new File(newBaseDir,file.getName());

            int taskLimit = Math.max(Runtime.getRuntime().availableProcessors(),1);
            AtomicInteger cnt = new AtomicInteger(0);
            List<RecursiveTask<String>> tasks = new ArrayList<>();
            try {
                BufferedWriter writer = write ? new BufferedWriter(new FileWriter(newFile)): null;
                while (iterator.hasNext()&&cnt.get()<maxSamples) {
                    if (cnt.getAndIncrement() % 10000 == 9999) {
                        taskLimit = Math.max(Runtime.getRuntime().availableProcessors(),1);
                        System.out.println("Iterated through: " + cnt.get());
                    }
                    if (tasks.size() >= taskLimit) {
                        String result = tasks.remove(0).join();
                        if(write&&result!=null) {
                            writer.write(result+"\n");
                        }
                    }
                    LabelledDocument doc = iterator.next();
                    RecursiveTask<String> task = new RecursiveTask<String>() {
                        @Override
                        protected String compute() {
                            return transformFunction.apply(doc);
                        }
                    };
                    task.fork();
                    tasks.add(task);
                }
                for(RecursiveTask<String> task : tasks) {
                    String result = task.join();
                    if(write && result!=null) {
                        writer.write(result+"\n");
                    }
                }
                if(write) {
                    writer.flush();
                    writer.close();
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
    }
}
