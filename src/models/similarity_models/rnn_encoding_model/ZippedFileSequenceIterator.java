package models.similarity_models.rnn_encoding_model;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ZippedFileSequenceIterator implements SequenceIterator<VocabWord> {
    private final File[] files;
    private BufferedReader reader;
    private AtomicLong counter = new AtomicLong(0);
    private Iterator<File> fileIterator;
    private long[] limits;
    private int iter;
    public ZippedFileSequenceIterator(File[] files, long... limits) {
        this.files=files;
        this.limits=limits;
        this.iter=0;
        this.fileIterator = newFileIterator();
    }

    private Iterator<File> newFileIterator() {
        List<File> f = IntStream.range(0,files.length).mapToObj(i->files[i]).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(f, new Random(System.currentTimeMillis()));
        return f.iterator();
    }

    @Override
    public boolean hasMoreSequences() {
        if(limits.length>0) {
            long l = limits[Math.min(limits.length-1,iter)];
            if(l>0&&counter.get()>=l) {
                System.out.println("Limit reached.");
                return false;
            }
        }
        return (fileIterator.hasNext());
    }

    private void setReaderToNextFile() throws IOException {
        File nextFile = fileIterator.next();
        reader = new BufferedReader(new InputStreamReader(new GzipCompressorInputStream(new FileInputStream(nextFile))));
    }

    @Override
    public Sequence<VocabWord> nextSequence() {
        try {
            if (reader == null) {
                setReaderToNextFile();
            }
            String line = reader.readLine();
            if (line == null) {
                setReaderToNextFile();
                line = reader.readLine();
                if (line == null) {
                    System.out.println("Sequence is null!");
                    return null;
                }
            }
            Sequence<VocabWord> sequence = new Sequence<>();
            for(String word : line.split("\\s+")) {
                VocabWord vocabWord = new VocabWord(1f,word);
                vocabWord.setElementFrequency(1);
                vocabWord.setSequencesCount(1);
                sequence.addElement(vocabWord);
            }
            if(counter.getAndIncrement()%100000==99999) {
                System.out.println("Finished: "+counter.get());
            }
            return sequence;
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error creating sequence!");
            return new Sequence<>();
        }

    }

    @Override
    public void reset() {
        System.out.println("Resetting iterator...");
        iter++;
        this.fileIterator = newFileIterator();
        counter.set(0);
        try {
            if(reader!=null)reader.close();
            reader=null;
        } catch(Exception e) {

        }
    }
}
