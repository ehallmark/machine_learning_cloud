package dl4j_neural_nets.phrase_tokenization;

import dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import seeding.Constants;
import tools.MinHeap;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/17/16.
 */
public class PhraseDeterminator {
    private SequenceIterator<VocabWord> iterator;
    private static File file = new File("phrases_set.obj");
    private List<BiGram> topBigramList;
    private Set<String> phrases;
    private Set<String> topPhraseSet;
    private double threshold = 0.00001;
    private int epoch;
    private int maxCapacity;

    public PhraseDeterminator(SequenceIterator<VocabWord> iterator, int maxCapacity) {
        this.maxCapacity=maxCapacity;
        epoch = 0;
        this.iterator=iterator;
        phrases=new HashSet<>();
        topBigramList = new ArrayList<>(maxCapacity);
    }

    public void determinePhrases() {
        epoch++;
        Word.reset();
        long iteration = 0;
        while(iterator.hasMoreSequences()) {
            if(iteration%1000==0) {
                System.out.println("Epoch: " + epoch + "  Iteration: " + iteration);
            }
            iteration+=1;
            Sequence<VocabWord> sequence = iterator.nextSequence();
            List<VocabWord> list = new LinkedList<>(sequence.getElements());
            if (!phrases.isEmpty()) {
                for (int i = 0; i < list.size() - 1; i++) {
                    VocabWord w1 = list.get(i);
                    VocabWord w2 = list.get(i + 1);
                    // Check for existing phrase
                    if (phrases.contains(w1.getWord() + "_" + w2.getWord())) {
                        list.remove(i);
                        list.set(i, new VocabWord(1.0, w1.getWord() + "_" + w2.getWord()));
                        i--;
                        continue;
                    }
                }
            }
            for(int i = 0; i < list.size()-1; i++) {
                VocabWord w1 = list.get(i);
                VocabWord w2 = list.get(i+1);
                if(Constants.STOP_WORD_SET.contains(w1.getWord()) || Constants.STOP_WORD_SET.contains(w2.getWord())) {
                    continue;
                }
                Word.increaseCountAndCoOccurence(w1.getWord(), w2.getWord());
            }
        }
        iterator.reset();
        MinHeap<BiGram> heap = new MinHeap<>(maxCapacity);
        Collection<Word> words = Word.getWords();
        for(Word word : words) {
            for(String nextWord : word.getNextWords()) {
                BiGram biGram = new BiGram(word,nextWord);
                if(biGram.score()>=threshold) {
                    heap.add(biGram);
                }
            }
        }
        topBigramList.clear();
        while(!heap.isEmpty()) {
            topBigramList.add(0,heap.remove());
        }

        topPhraseSet = topBigramList.stream().map(biGram->biGram.toString()).collect(Collectors.toSet());
        phrases.addAll(topPhraseSet);
        threshold/=2.0;
    }

    public List<BiGram> getBigrams() {
        return topBigramList;
    }

    public Set<String> getPhrasesSet() {
        return topPhraseSet;
    }

    public void save() throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        oos.writeObject(topPhraseSet);
        oos.flush();
        oos.close();
    }

    public static Set<String> load() throws IOException,ClassNotFoundException {
        if(!file.exists()) return null;
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
        Set<String> toReturn = (Set<String>)ois.readObject();
        ois.close();
        return toReturn;
    }

    public static void main(String[] args) throws Exception {
        SequenceIterator<VocabWord> test = DatabaseIteratorFactory.SamplePatentSequenceIterator();
        int maxNumResults = 100000;
        PhraseDeterminator determinator = new PhraseDeterminator(test,maxNumResults);
        determinator.determinePhrases();

        // run again to find larger phrases
        determinator.determinePhrases();
        //determinator.determinePhrases();

        // now print results
        for(String bigram: determinator.getPhrasesSet()) {
            System.out.println("Phrase: "+bigram);
        }

        determinator.save();

        // test
        TestPhraseDeterminator.main(new String[]{});
    }
}
