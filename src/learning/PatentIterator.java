package learning;

import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;


import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * Created by ehallmark on 7/11/16.
 */
public class PatentIterator implements LabelAwareIterator {
    protected Set<String> patents;
    protected Map<String, Set<String>> labelsMap;
    protected File labelFile;
    protected String currentPatent;
    protected Iterator<String> currentSentenceIterator;
    protected List<File> filesToIterate;
    protected Iterator<File> iterator;
    protected List<String> currentLabels;
    protected LabelsSource source;
    protected static Random rand = new Random(41);

    public PatentIterator(File sourceFile, File labelFile) throws IOException {
        this.labelFile=labelFile;
        this.labelsMap = readPatentLabelsMap();
        this.patents = labelsMap.keySet();
        this.source = new LabelsSource(new LinkedList<>(patents));
        filesToIterate = new LinkedList<>();
        for(File patentFolder : sourceFile.listFiles()) {
            if(patentFolder.isDirectory()&&patents.contains(patentFolder.getName().replaceAll("/",""))) {
                filesToIterate.addAll(Arrays.asList(patentFolder.listFiles()));
            }
        }
        this.iterator=filesToIterate.iterator();
    }

    @Override
    public boolean hasNextDocument() {
        return iterator.hasNext() || (currentSentenceIterator!=null && currentSentenceIterator.hasNext());
    }

    @Override
    public LabelledDocument nextDocument() {
        if(currentSentenceIterator==null || !currentSentenceIterator.hasNext()) {
            File file = iterator.next();
            List<String> lines;
            try {
                lines = Files.readAllLines(file.toPath());
            } catch(IOException ioe) {
                ioe.printStackTrace();
                throw new RuntimeException("Unable to read file "+file.getName());
            }
            if(lines!=null && !lines.isEmpty()) {
                currentPatent = file.getParentFile().getName().replaceAll("/", "");
                currentLabels = new ArrayList<>(labelsMap.get(currentPatent));
                currentSentenceIterator=lines.iterator();
            } else {
                if(hasNextDocument()) return nextDocument();
                else {
                    LabelledDocument doc = new LabelledDocument();
                    doc.setLabel(currentLabel());
                    doc.setContent("");
                    return doc;
                }
                //throw new RuntimeException("File "+file.getName()+" has no lines!");
            }
        }
        LabelledDocument doc = new LabelledDocument();
        doc.setContent(currentSentenceIterator.next());
        doc.setLabel(currentLabel());
        return doc;
    }

    @Override
    public void reset() {
        iterator=filesToIterate.iterator();
        currentSentenceIterator=null;
    }

    @Override
    public LabelsSource getLabelsSource() {
        return source;
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

    public String currentLabel() {
        return currentLabels.get(Math.abs(rand.nextInt())%currentLabels.size());
    }

}
