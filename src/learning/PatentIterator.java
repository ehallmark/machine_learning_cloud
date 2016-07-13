package learning;

import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by ehallmark on 7/11/16.
 */
public class PatentIterator implements SentenceIterator {
    protected Set<String> patents;
    protected Map<String, Set<String>> labelsMap;
    protected File labelFile;
    protected Iterator<File> iterator;
    protected Iterator<String> innerIterator;
    protected SentencePreProcessor preProcessor;
    protected List<File> filesToIterate;
    protected String currentPatent;

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
        return iterator.hasNext() || (innerIterator!=null && innerIterator.hasNext());
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
        if(innerIterator==null || !innerIterator.hasNext()) {
            try {
                File file = iterator.next();
                currentPatent = file.getParentFile().getName().replaceAll("/", "");
                String sentence = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
                innerIterator = Arrays.asList(sentence.split(".")).iterator();
                return sentence;
            } catch (IOException ioe) {
                ioe.printStackTrace();
                throw new RuntimeException("CANNOT READ NEXT SENTENCE");
            }
        }
        if(preProcessor!=null)return preProcessor.preProcess(innerIterator.next());
        else return innerIterator.next();
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
}
