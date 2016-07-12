package learning;

import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import seeding.Constants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ehallmark on 7/12/16.
 */
public class ParagraphIterator extends PatentIterator implements LabelAwareSentenceIterator {
    public ParagraphIterator(File sourceFile, File labelFile) throws IOException {
        super(labelFile);
        filesToIterate = new LinkedList<>();
        for(File patentFolder : sourceFile.listFiles()) {
            if(patentFolder.isDirectory()&&patents.contains(patentFolder.getName().replaceAll("/",""))) {
                for(File potentialMatch : patentFolder.listFiles()) {
                    if(potentialMatch.getName().equals(Constants.ABSTRACT)||potentialMatch.getName().equals(Constants.DESCRIPTION)) {
                        filesToIterate.add(potentialMatch);
                    }
                }
            }
        }
        this.iterator=filesToIterate.iterator();
    }

    @Override
    public String currentLabel() {
        return null;
    }

    @Override
    public List<String> currentLabels() {
        return new ArrayList<>(labelsMap.get(currentPatent));
    }
}
