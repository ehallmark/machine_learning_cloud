package seeding;

import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/19/16.
 */
public class FasterFilenamesLabelAwareIterator implements LabelAwareIterator {

    protected File folder;
    protected Iterator<Path> iter;
    protected AtomicInteger position = new AtomicInteger(0);
    protected LabelsSource labelsSource;
    protected boolean absPath = false;
    protected long lastTime;

    /*
        Please keep this method protected, it's used in tests
     */
    protected FasterFilenamesLabelAwareIterator() {

    }

    protected FasterFilenamesLabelAwareIterator(@NonNull File folder, @NonNull LabelsSource source) throws IOException {
        iter = Files.newDirectoryStream(folder.toPath()).iterator();
        this.labelsSource = source;
        this.folder=folder;
        lastTime = System.currentTimeMillis();

    }

    @Override
    public LabelledDocument nextDocument() {
        File fileToRead = iter.next().toFile();
        String label = (absPath) ? fileToRead.getAbsolutePath() : fileToRead.getName();
        labelsSource.storeLabel(label);
        try {
            LabelledDocument document = new LabelledDocument();
            document.setContent(FileUtils.readFileToString(fileToRead));
            document.setLabel(label);
            int pos = position.getAndIncrement();
            if(pos%1000==999) {
                System.out.println("Time to complete 1000 patents: "+new Double(System.currentTimeMillis()-lastTime)/(1000)+" seconds");
                lastTime = System.currentTimeMillis();
                position.set(0);
            }
            return document;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    @Override
    public boolean hasNextDocument() {
        return iter.hasNext();
    }


    @Override
    public void reset() {
        try {
            iter = Files.newDirectoryStream(folder.toPath()).iterator();
        } catch(IOException ioe) {
            throw new RuntimeException("IO Exception when resetting iterator");
        }
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


        public FasterFilenamesLabelAwareIterator build() throws IOException{
            // search for all files in all folders provided

            LabelsSource source = new LabelsSource(labels);
            FasterFilenamesLabelAwareIterator iterator = new FasterFilenamesLabelAwareIterator(foldersToScan.get(0), source);
            iterator.absPath = this.absPath;

            return iterator;
        }
    }
}
