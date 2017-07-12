package models.dl4j_neural_nets.iterators.sequences;

import models.graphical_models.related_docs.RelatedAssetsGraph;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import tools.AssigneeTrimmer;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Created by ehallmark on 11/19/16.
 */
public class DatabaseIteratorFactory {
    private static final String PatentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=password&tcpKeepAlive=true";
    //private static final String CompDBUrl = "jdbc:postgresql://localhost/compdb_production?user=postgres&password=&tcpKeepAlive=true";
    private static final String GatherDBUrl = "jdbc:postgresql://localhost/gather_production?user=postgres&password=&tcpKeepAlive=true";

    private static final String ParagraphTokensQuery = "select pub_doc_number, assignees, tokens from paragraph_tokens";
    private static final String PatentTextQuery = "select pub_doc_number,regexp_replace(lower(abstract),'[^a-z ]',' ','g'),regexp_replace(lower(substring(description from 1 for 10000)),'[^a-z ]','','g') from patent_grant where pub_doc_number=ANY(?)";
    private static final String PatentSampleSequenceQuery = "select pub_doc_number,tokens from paragraph_tokens tablesample system(?)";
    private static final String ParagraphSampleTokensQuery = "select pub_doc_number, assignees, tokens from paragraph_tokens limit ?";
    private static final String GatherTechnologyQuery="select upper(name), array_remove(string_to_array(regexp_replace(lower(unnest(avals(claims))),'[^a-z ]',' ','g'), ' '),'') from patents as p join assessments as a on (p.id=a.patent_id) join assessment_technologies as at on (a.id=at.assessment_id) join technologies as t on (at.technology_id=t.id) order by random() limit ? offset ?";

    public static SequenceIterator<VocabWord> PatentParagraphSamplingSequenceIterator(int numEpochs, int limit) throws SQLException {
        return new DatabaseSequenceIterator.Builder(ParagraphSampleTokensQuery,PatentDBUrl)
                .addLabelIndex(1)
                .addLabelArrayIndex(2) // inventors
                .addTextIndex(3)
                .setParameterAsInt(1,limit)
                .setNumEpochs(numEpochs)
                .setFetchSize(5)
                .build();
    }

    public static SequenceIterator<VocabWord> PatentParagraphSequenceIterator(int numEpochs) throws SQLException {
        RelatedAssetsGraph relatedAssetsGraph = RelatedAssetsGraph.get();
        return new DatabaseSequenceIterator.Builder(ParagraphTokensQuery,PatentDBUrl)
                .addLabelIndex(1, label -> String.valueOf(relatedAssetsGraph.indexForAsset(label)))
                .addLabelArrayIndex(2, label -> AssigneeTrimmer.standardizedAssignee(label))
                .addTextIndex(3)
                .setNumEpochs(numEpochs)
                .setFetchSize(5)
                .build();
    }

    public static SequenceIterator<VocabWord> SpecificPatentParagraphSequenceIterator(Collection<String> patents) throws SQLException {
        TokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new CommonPreprocessor());
        SentenceTransformer transformer = new SentenceTransformer.Builder().allowMultithreading(false)
                .iterator(new DatabaseTextIterator.Builder(PatentTextQuery,PatentDBUrl)
                        .addTextIndex(2) // abstract
                        //.addTextIndex(3) // description
                        .addLabelIndex(1)
                        .setFetchSize(5)
                        .setParameterAsArray(1,patents.toArray(),"varchar")
                        .build())
                .tokenizerFactory(tf)
                .build();
        return new AbstractSequenceIterator.Builder<>(transformer).build();
    }

    public static DatabaseTextIterator SpecificPatentParagraphTextIterator(Collection<String> patents) throws SQLException {
        return new DatabaseTextIterator.Builder(PatentTextQuery,PatentDBUrl)
                        .addTextIndex(2) // abstract
                        //.addTextIndex(3) // description
                        .addLabelIndex(1)
                        .setFetchSize(5)
                        .setParameterAsArray(1,patents.toArray(),"varchar")
                        .build();
    }


    public static DatabaseSequenceIterator SamplePatentSequenceIterator() throws SQLException {
        return new DatabaseSequenceIterator.Builder(PatentSampleSequenceQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();

    }

    public static DatabaseSequenceIterator GatherTechnologySequenceIterator(int offset, int limit) throws SQLException {
        return new DatabaseSequenceIterator.Builder(GatherTechnologyQuery,GatherDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .setSeed(0.5)
                .setParameterAsInt(1,limit)
                .setParameterAsInt(2,offset)
                .build();

    }
}
