package seeding;

import org.deeplearning4j.text.documentiterator.FilenamesLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;

/**
 * Created by ehallmark on 8/19/16.
 */
public class FasterFilenamesLabelAwareIterator extends FilenamesLabelAwareIterator {

    @Override
    public LabelledDocument nextDocument() {
        File fileToRead = files.get(position.getAndIncrement());
        String label = (absPath) ? fileToRead.getAbsolutePath() : fileToRead.getName();
        labelsSource.storeLabel(label);
        try {
            LabelledDocument document = new LabelledDocument();
            String content = new String(Files.readAllBytes(fileToRead.toPath()));
            document.setContent(content);
            document.setLabel(label);

            return document;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
