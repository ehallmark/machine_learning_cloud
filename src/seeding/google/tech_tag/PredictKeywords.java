package seeding.google.tech_tag;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import seeding.Constants;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class PredictKeywords {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Connection seedConn = Database.getConn();
        Connection ingestConn = Database.newSeedConn();

        PreparedStatement seedPs = seedConn.prepareStatement("select p.family_id,p.abstract from big_query_patent_english_abstract as p full outer join big_query_keywords_all as k on (p.family_id=k.family_id) where k.family_id is null");
        seedPs.setFetchSize(25);
        ResultSet rs = seedPs.executeQuery();
        PreparedStatement ingestPs = ingestConn.prepareStatement("insert into big_query_keywords_all (family_id,keywords) values (?,?) on conflict (family_id) do update set keywords=excluded.keywords");

        AtomicLong cnt = new AtomicLong(0);
        while(rs.next()) {
            String famId = rs.getString(1);
            String text = rs.getString(2);
            Annotation annotation = new Annotation(text);

            pipeline.annotate(annotation, d -> {
                try {
                    List<String> keywords = extractKeywords(d);
                    if(keywords!=null&&keywords.size()>0) {
                        ingestPs.setString(1,famId);
                        ingestPs.setArray(2, ingestConn.createArrayOf("varchar", keywords.toArray()));
                        ingestPs.executeUpdate();
                    }
                    if(cnt.getAndIncrement() % 10000==9999) {
                        System.out.println("Completed: "+cnt.get());
                        ingestConn.commit();
                    }

                } catch(Exception e) {
                    e.printStackTrace();
                }
            });
        }

        ingestConn.commit();
    }

    private static final Set<String> stopWords = new HashSet<>();
    static {
        stopWords.add("in");
        stopWords.add("of");
        stopWords.add("to");
        stopWords.add("and");
        stopWords.add("or");
        stopWords.add("with");
        stopWords.add("for");
    }
    public static final Collection<String> validPOS = Arrays.asList("JJ", "JJR", "JJS", "NN", "NNS", "NNP", "NNPS", "VBG", "VBN");
    public static final Collection<String> adjectivesPOS = Arrays.asList("JJ", "JJR", "JJS");
    public static final int maxPhraseLength = 3;
    public static List<String> extractKeywords(Annotation d) {
        List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
        List<String> data = new ArrayList<>();
        for (CoreMap sentence : sentences) {
            // traversing the words in the current sentence
            // a CoreLabel is a CoreMap with additional token-specific methods
            String prevLemma = null;
            String prevPrevLemma= null;
            String prevPos = null;
            String prevPrevPos = null;
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                // this is the text of the token
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                int nonAlpha = 0;
                for (int i = 0; i < word.length(); i++) {
                    if (!Character.isAlphabetic(word.charAt(i))) {
                        nonAlpha++;
                    }
                }
                boolean valid = nonAlpha <= word.length()/2;
                if (!valid) continue;

                // could be the stem
                String lemma = word.toLowerCase();
                try {
                    String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                    if (lemma.length() >= 3) {
                        // this is the POS tag of the token
                        if (validPOS.contains(pos)) {
                            // don't want to end in adjectives (nor past tense verb)
                            if(!Constants.STOP_WORD_SET.contains(lemma)) data.add(lemma);
                            if (!adjectivesPOS.contains(pos)) {
                                if (maxPhraseLength > 1) {
                                    if (prevLemma != null && !prevLemma.equals(lemma)) {
                                        long numVerbs = Stream.of(pos, prevPos).filter(p -> p != null && p.startsWith("V")).count();
                                        if (numVerbs <= 1) {
                                            if(validPOS.contains(prevPos) && prevLemma.length()>=3) {
                                                if(!Constants.STOP_WORD_SET.contains(prevLemma)&&!Constants.STOP_WORD_SET.contains(lemma)) {
                                                    data.add(String.join(" ", prevLemma, lemma));
                                                }
                                            }
                                            if (maxPhraseLength > 2) {
                                                if (prevPrevLemma != null && !prevLemma.equals(prevPrevLemma)) {
                                                    numVerbs = Stream.of(pos, prevPos, prevPrevPos).filter(p -> p != null && p.startsWith("V")).count();
                                                    if (numVerbs <= 1) {
                                                        if(validPOS.contains(prevPrevPos) && prevPrevLemma.length()>=3) {
                                                            if (validPOS.contains(prevPos) || stopWords.contains(prevLemma)) {
                                                                if (!Constants.STOP_WORD_SET.contains(prevPrevLemma) && !Constants.STOP_WORD_SET.contains(lemma)) {
                                                                    data.add(String.join(" ", prevPrevLemma, prevLemma, lemma));
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    prevPrevLemma = prevLemma;
                    prevLemma = lemma;
                    prevPrevPos = prevPos;
                    prevPos = pos;

                } catch (Exception e) {
                    System.out.println("Error while stemming: " + lemma);
                    prevLemma = null;
                    prevPrevLemma = null;
                    prevPos = null;
                    prevPrevPos = null;
                }
            }
        }
        return data;
    }

    public static class FamilyIdAnnotation implements CoreAnnotation<String> {
        public FamilyIdAnnotation() {
        }

        public Class<String> getType() {
            return String.class;
        }
    }
}


