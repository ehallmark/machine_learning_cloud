package seeding;

import lombok.NonNull;
import org.deeplearning4j.text.documentiterator.FilenamesLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/19/16.
 */
public class FasterFilenamesLabelAwareIterator implements LabelAwareIterator {

    protected List<File> files;
    protected AtomicInteger position = new AtomicInteger(0);
    protected LabelsSource labelsSource;
    protected boolean absPath = false;
    protected long lastTime;

    /*
        Please keep this method protected, it's used in tests
     */
    protected FasterFilenamesLabelAwareIterator() {

    }

    protected FasterFilenamesLabelAwareIterator(@NonNull List<File> files, @NonNull LabelsSource source) {
        this.files = files;
        this.labelsSource = source;
        lastTime = System.currentTimeMillis();
    }

    @Override
    public LabelledDocument nextDocument() {
        int pos = position.getAndIncrement();
        File fileToRead = files.get(pos);
        String label = (absPath) ? fileToRead.getAbsolutePath() : fileToRead.getName();
        labelsSource.storeLabel(label);
        try {
            LabelledDocument document = new LabelledDocument();
            String content = new String(Files.readAllBytes(fileToRead.toPath()));
            document.setContent(content);
            document.setLabel(label);
            if(pos%1000==999) {
                System.out.println("Time to complete 1000 patents: "+new Double(System.currentTimeMillis()-lastTime)/(1000*60)+" seconds");
                lastTime = System.currentTimeMillis();
            }
            return document;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    @Override
    public boolean hasNextDocument() {
        return position.get() < files.size();
    }


    @Override
    public void reset() {
        position.set(0);
    }

    @Override
    public LabelsSource getLabelsSource() {
        return labelsSource;
    }

    public static class Builder {
        protected List<File> foldersToScan = new ArrayList<>();

        private List<File> fileList = new ArrayList<>();
        private List<String> labels = new ArrayList<>();
        private boolean absPath = false;

        public Builder() {

        }

        /**
         * Root folder for labels -> documents.
         * Each subfolder name will be presented as label, and contents of this folder will be represented as LabelledDocument, with label attached
         *
         * @param folder folder to be scanned for labels and files
         * @return
         */
        public FasterFilenamesLabelAwareIterator.Builder addSourceFolder(@NonNull File folder) {
            foldersToScan.add(folder);
            return this;
        }

        public FasterFilenamesLabelAwareIterator.Builder useAbsolutePathAsLabel(boolean reallyUse) {
            this.absPath = reallyUse;
            return this;
        }

        private void scanFolder(File folderToScan) {
            File[] files = folderToScan.listFiles();
            if (files == null || files.length ==0 ) return;


            for (File fileLabel: files) {
                if (fileLabel.isDirectory()) {
                    scanFolder(fileLabel);
                } else {
                    fileList.add(fileLabel);
                }
            }
        }

        public FasterFilenamesLabelAwareIterator build() {
            // search for all files in all folders provided


            for (File file: foldersToScan) {
                if (!file.isDirectory()) continue;
                scanFolder(file);
            }

            LabelsSource source = new LabelsSource(labels);
            FasterFilenamesLabelAwareIterator iterator = new FasterFilenamesLabelAwareIterator(fileList, source);
            iterator.absPath = this.absPath;

            return iterator;
        }
    }
}
