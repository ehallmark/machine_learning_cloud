package learning;

import org.deeplearning4j.text.sentenceiterator.LineSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by ehallmark on 7/11/16.
 */
public class PatentIterator implements SentenceIterator, LabelAwareSentenceIterator {
    protected Set<String> patents;
    protected Map<String, Set<String>> labelsMap;
    protected File labelFile;
    protected SentencePreProcessor preProcessor;
    protected String currentPatent;
    protected LineSentenceIterator currentSentenceIterator;
    protected List<File> filesToIterate;
    protected Iterator<File> iterator;
    protected List<String> currentLabels;
    protected static Random rand = new Random(41);

    public PatentIterator(File labelFile) throws IOException {
        this.labelFile=labelFile;
        this.labelsMap = readPatentLabelsMap();
        this.patents = labelsMap.keySet();
    }

    public PatentIterator(File sourceFile, File labelFile) throws IOException {
        this.labelFile=labelFile;
        this.labelsMap = readPatentLabelsMap();
        this.patents = labelsMap.keySet();
        filesToIterate = new LinkedList<>();
        for(File patentFolder : sourceFile.listFiles()) {
            if(patentFolder.isDirectory()&&patents.contains(patentFolder.getName().replaceAll("/",""))) {
                filesToIterate.addAll(Arrays.asList(patentFolder.listFiles()));
            }
        }
        this.iterator=filesToIterate.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext() || (currentSentenceIterator!=null && currentSentenceIterator.hasNext());
    }

    @Override
    public void reset() {
        iterator=filesToIterate.iterator();
    }

    @Override
    public void finish() {

    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return preProcessor;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor preProcessor) {
        this.preProcessor=preProcessor;
    }

    @Override
    public String nextSentence() {
        if(currentSentenceIterator==null || !currentSentenceIterator.hasNext()) {
            if(currentSentenceIterator!=null)currentSentenceIterator.finish();
            File file = iterator.next();
            currentPatent = file.getParentFile().getName().replaceAll("/", "");
            currentLabels = new ArrayList<>(labelsMap.get(currentPatent));
            currentSentenceIterator=new LineSentenceIterator(file);
        }
        if(preProcessor!=null)return preProcessor.preProcess(currentSentenceIterator.nextSentence());
        else return currentSentenceIterator.nextSentence();
    }

    protected Map<String, Set<String>> readPatentLabelsMap() throws IOException {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(labelFile)));
        try {
            return (Map<String,Set<String>>) ois.readObject();
        } catch (ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            return null;
        }
    }

    @Override
    public String currentLabel() {
        return currentLabels.get(rand.nextInt()%currentLabels.size());
    }

    @Override
    public List<String> currentLabels() {
        return currentLabels;
    }
}
