package synonyms;

import lombok.Getter;
import lombok.NonNull;

import java.util.HashSet;
import java.util.Set;

public class WordSynonym {
    @Getter
    private String word;
    @Getter
    private int meaningIdx;
    @Getter
    private String pos;
    @Getter
    private Set<String> synonyms;
    public WordSynonym(@NonNull String word, @NonNull String pos, int meaningIdx) {
        this.word=word;
        this.pos=pos;
        this.meaningIdx=meaningIdx;
        this.synonyms = new HashSet<>();
    }

    public void addSynonym(String synonym) {
        this.synonyms.add(synonym);
    }


}
