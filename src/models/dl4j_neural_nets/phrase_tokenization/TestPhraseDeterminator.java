package models.dl4j_neural_nets.phrase_tokenization;

import tools.Emailer;

import java.util.Set;

/**
 * Created by ehallmark on 12/18/16.
 */
public class TestPhraseDeterminator {
    public static void main(String[] args) throws Exception {
        Set<String> phrases = PhraseDeterminator.load();
        new Emailer(String.join("\n",phrases));
        System.out.println("Num phrases: "+phrases.size());
    }
}
