package dl4j_neural_nets.iterators.sequences;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by ehallmark on 11/19/16.
 */
public class DatabaseIteratorFactory {
    private static final String PatentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private static final String CompDBUrl = "jdbc:postgresql://localhost/compdb_production?user=postgres&password=&tcpKeepAlive=true";
    private static final String GatherDBUrl = "jdbc:postgresql://localhost/gather_production?user=postgres&password=&tcpKeepAlive=true";

    private static final String ClaimTextQueryWithLimitAndOffsetWithOrder = "select pub_doc_number, regexp_replace(lower(claim_text),'[^a-z ]','','g') from patent_grant_claim_recent where claim_text is not null and char_length(claim_text) > 100 order by pub_doc_number desc offset ? limit ?";
    private static final String ClaimTextQueryWithLimitAndOffset = "select pub_doc_number, regexp_replace(lower(claim_text),'[^a-z ]','','g') from patent_grant_claim_recent where claim_text is not null and char_length(claim_text) > 100 offset ? limit ?";
    private static final String ClaimTextQuery = "select pub_doc_number, regexp_replace(lower(claim_text),'[^a-z ]','','g') from patent_grant_claim_recent where claim_text is not null and char_length(claim_text) > 100 order by random()";
    private static final String ParagraphTokensQuery = "select pub_doc_number, classifications, inventors, tokens from paragraph_tokens";
    private static final String ParentClaimTextQuery = "select pub_doc_number, regexp_replace(lower(claim_text),'[^a-z ]','','g') from patent_grant_claim_recent where claim_text is not null and parent_claim_id is null and char_length(claim_text) > 100";
    private static final String PatentTextQueryWithLimitAndOffset = "select pub_doc_number,regexp_replace(lower(abstract),'[^a-z ]','','g'),regexp_replace(lower(substring(description from 1 for least(char_length(description),10000))),'[^a-z ]','','g') from patent_grant_recent where description is not null order by pub_doc_number desc offset ? limit ?";
    private static final String PatentTextQuery = "select pub_doc_number,regexp_replace(lower(abstract),'[^a-z ]','','g'),regexp_replace(lower(substring(description from 1 for 10000)),'[^a-z ]','','g') from patent_grant_recent";
    private static final String PatentSequenceQuery = "select pub_doc_number,tokens from patent_description_tokens";
    private static final String PatentSentenceSequenceQuery = "select pub_doc_number,tokens from patent_sentence_tokens";
    private static final String PatentSampleSequenceQuery = "select pub_doc_number,tokens from paragraph_tokens tablesample system(20)";
    private static final String PatentClaimSequenceQuery = "select pub_doc_number,tokens from patent_claim_tokens";
    private static final String ClaimTextQueryByPatents = "select pub_doc_number,regexp_replace(lower(claim_text),'[^a-z ]','','g') from patent_grant_claim_recent where pub_doc_number=ANY(?) and char_length(claim_text) > 100 ";
    private static final String ValuablePatentsQuery = "select case when pub_doc_number = ANY(?) then 'YES' else 'NO' end as valuable,regexp_replace(lower(claim_text),'[^a-z ]',' ','g') from (select * from patent_grant_claim_recent where char_length(claim_text) > 500 and (pub_doc_number = ANY(?) or pub_doc_number=ANY(?))) as temp order by random()";
    private static final String GatherTechnologyQuery="select upper(name), array_remove(string_to_array(regexp_replace(lower(unnest(avals(claims))),'[^a-z ]',' ','g'), ' '),'') from patents as p join assessments as a on (p.id=a.patent_id) join assessment_technologies as at on (a.id=at.assessment_id) join technologies as t on (at.technology_id=t.id) order by random() limit ? offset ?";
    private static final String GatherValueQuery = "";
    private static final String AbstractSentenceQuery = "select pub_doc_number, unnest(string_to_array(regexp_replace(lower(abstract),'[^a-z\\. ]',' ','g'),'.')) from patent_grant_recent";
    private static final String DescriptionSentenceQuery = "select pub_doc_number, unnest(string_to_array(regexp_replace(lower(description),'[^a-z\\. ]',' ','g'),'.')) from patent_grant_recent";
    private static final String ClaimSentenceQuery = "select pub_doc_number, unnest(string_to_array(regexp_replace(lower(claim_text),'[^a-z\\. ]',' ','g'),'.')) from patent_grant_claim_recent";


    public static LabelAwareSentenceIterator DescriptionSentenceIterator() throws SQLException {
        return new DatabaseTextIterator.Builder(DescriptionSentenceQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();
    }

    public static LabelAwareSentenceIterator ClaimSentenceIterator() throws SQLException {
        return new DatabaseTextIterator.Builder(ClaimSentenceQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();
    }

    public static SequenceIterator<VocabWord> PatentParagraphSequenceIterator(int numEpochs) throws SQLException {
        return new DatabaseSequenceIterator.Builder(ParagraphTokensQuery,PatentDBUrl)
                .addLabelIndex(1)
                .addLabelArrayIndex(2) // classification vectors
                .addLabelArrayIndex(3) // inventors
                .addTextIndex(4)
                .setNumEpochs(numEpochs)
                .setFetchSize(5)
                .build();
    }

    public static LabelAwareSentenceIterator AbstractSentenceIterator() throws SQLException {
        return new DatabaseTextIterator.Builder(AbstractSentenceQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();
    }

    public static DatabaseTextIterator ParentClaimTextIterator() throws SQLException {
        return new DatabaseTextIterator.Builder(ParentClaimTextQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();
    }

    public static DatabaseTextIterator ClaimTextIterator() throws SQLException {
        return new DatabaseTextIterator.Builder(ClaimTextQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();

    }

    public static DatabaseTextIterator PatentTextIterator(int offset, int limit) throws SQLException {
        return new DatabaseTextIterator.Builder(PatentTextQueryWithLimitAndOffset,PatentDBUrl)
                .addTextIndex(2)
                //.addTextIndex(3)
                .addLabelIndex(1)
                .setFetchSize(5)
                .setParameterAsInt(1,offset)
                .setParameterAsInt(2,limit)
                .build();
    }

    public static DatabaseTextIterator PatentTextIterator() throws SQLException {
        return new DatabaseTextIterator.Builder(PatentTextQuery,PatentDBUrl)
                .addTextIndex(2)
                .addTextIndex(3)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();

    }

    public static DatabaseSequenceIterator PatentSequenceIterator() throws SQLException {
        return new DatabaseSequenceIterator.Builder(PatentSequenceQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();

    }

    public static DatabaseSequenceIterator PatentSentenceSequenceIterator() throws SQLException {
        return new DatabaseSequenceIterator.Builder(PatentSentenceSequenceQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();

    }

    public static DatabaseSequenceIterator SamplePatentSequenceIterator() throws SQLException {
        return new DatabaseSequenceIterator.Builder(PatentSampleSequenceQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();

    }

    public static DatabaseSequenceIterator PatentClaimSequenceIterator() throws SQLException {
        return new DatabaseSequenceIterator.Builder(PatentClaimSequenceQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .build();
    }

    public static DatabaseTextIterator SpecificClaimTextIterator(List<String> patents) throws SQLException {
        return new DatabaseTextIterator.Builder(ClaimTextQueryByPatents,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .setParameterAsArray(1,patents.toArray(),"varchar")
                .build();
    }

    public static DatabaseTextIterator ValuableClaimTextIterator(List<String> valuablePatents, List<String> unvaluablePatents) throws SQLException {
        return new DatabaseTextIterator.Builder(ValuablePatentsQuery,PatentDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .setParameterAsArray(1, valuablePatents.toArray(), "varchar")
                .setParameterAsArray(2, valuablePatents.toArray(), "varchar")
                .setParameterAsArray(3, unvaluablePatents.toArray(), "varchar")
                .build();
    }

    public static DatabaseTextIterator GatherValueTextIterator(int offset, int limit) throws SQLException {
        return new DatabaseTextIterator.Builder(GatherValueQuery,GatherDBUrl)
                .addTextIndex(2)
                .addLabelIndex(1)
                .setFetchSize(5)
                .setParameterAsInt(1,offset)
                .setParameterAsInt(2,limit)
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
