package user_interface.ui_models.filters;

import com.google.gson.Gson;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.NonNull;
import lombok.Setter;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import seeding.google.word2vec.Word2VecManager;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.*;
import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 7/9/2017.
 */
public class AdvancedKeywordFilter extends AbstractFilter {
    public static final String OR = "|";
    public static final String AND = "+";
    public static final String NOT = "-";
    public static final String OR_FRIENDLY = "OR";
    public static final String AND_FRIENDLY = "AND";
    public static final String NOT_FRIENDLY = "NOT";

    protected Boolean includeSynonyms;
    @Setter
    protected String queryStr;

    public AdvancedKeywordFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }


    @Override
    public AbstractFilter dup() {
        return new AdvancedKeywordFilter(attribute,filterType);
    }


    protected static QueryBuilder handleQuery(Query query, Function<Query,QueryBuilder> leafFunction, BoolQueryBuilder builder) {
        if(query instanceof BooleanQuery) {
            for(BooleanClause clause : ((BooleanQuery) query).clauses()) {
                BoolQueryBuilder innerBuilder = QueryBuilders.boolQuery();
                QueryBuilder innerQuery = handleQuery(clause.getQuery(), leafFunction, innerBuilder);
                if(clause.isProhibited()) {
                    builder = builder.mustNot(innerQuery);
                } else if(clause.isRequired()) {
                    builder = builder.must(innerQuery);
                } else {
                    builder = builder.should(innerQuery);
                }
            }
        } else {
            builder = builder.must(leafFunction.apply(query));
        }
        return builder;
    }


    @Override
    public QueryBuilder getFilterQuery() {
        if(queryStr==null) {
            return QueryBuilders.boolQuery();
        } else {
            if(includeSynonyms!=null && includeSynonyms) {
                // manipulate query string
                SimpleQueryParser query = new SimpleQueryParser(new KeywordAnalyzer(), "");
                query.setDefaultOperator(BooleanClause.Occur.MUST);
                Query lucene = query.parse(queryStr);
                Function<Query, QueryBuilder> leafFunction = q -> {
                    String qStr = q.toString();
                    System.out.println("qStr: "+qStr);
                    Operator op = Operator.AND;
                    if (q instanceof TermQuery) {
                        if(qStr.replaceAll("[^A-Za-z0-9]","").length()!=qStr.length()) {
                            qStr = "\"" + qStr + "\"";
                        } else {
                            // find synonyms
                            System.out.println("Searching for synonyms...");
                            String word = qStr.toLowerCase().trim();
                            Map<String, Collection<String>> synonymMap = Word2VecManager.synonymsFor(Collections.singletonList(word), 5, 0.0);
                            System.out.println("Synonyms for "+word+": "+new Gson().toJson(synonymMap));
                            List<String> valid = new ArrayList<>();
                            valid.add(word);
                            Collection<String> synonyms = synonymMap.getOrDefault(word, Collections.emptyList());
                            valid.addAll(synonyms);
                            qStr = String.join(" ", valid);
                            System.out.println("QStr After: "+qStr);
                            op = Operator.OR;
                        }
                    }
                    return QueryBuilders.simpleQueryStringQuery(qStr).defaultOperator(op)
                            .analyzeWildcard(true)
                            .field(getFullPrerequisite());
                };
                QueryBuilder ret = handleQuery(lucene, leafFunction, QueryBuilders.boolQuery());
                System.out.println("Synonym query: "+ret);
                return ret;
            }
            return QueryBuilders.simpleQueryStringQuery(queryStr)
                    .defaultOperator(Operator.AND)
                    .analyzeWildcard(true) // might be slow but Scott Hicks uses it...
                    .field(getFullPrerequisite());
        }
    }

    @Override
    public List<String> getInputIds() {
        List<String> list = new ArrayList<>(super.getInputIds());
        if(Attributes.ATTRIBUTES_WITH_SYNONYMS.contains(getAttribute().getName())) {
            list.add(getUseSynonymsId());
        }
        return list;
    }

    public String getUseSynonymsId() {
        return getId()+"_use_synonyms";
    }


    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("Include Filter not supported by scripts");
    }

    public boolean isActive() {return queryStr!=null && queryStr.length()>0; }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        queryStr = String.join("", SimilarPatentServer.extractArray(req,getName()));
        // check for boolean operators
        queryStr = queryStr.replace(" "+OR_FRIENDLY+" ",OR);
        queryStr = queryStr.replace(" "+AND_FRIENDLY+" ",AND);
        queryStr = queryStr.replace(" "+NOT_FRIENDLY+" ",NOT);
        includeSynonyms = SimilarPatentServer.extractBool(req, getUseSynonymsId());
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        boolean allowSynonyms = Attributes.ATTRIBUTES_WITH_SYNONYMS.contains(getAttribute().getName());
        ContainerTag tag = div().with(
                button("Syntax").withClass("miniTip btn btn-sm btn-outline-secondary"),
                textarea().withId(getId()).withClass("form-control").attr("placeholder","Example: (\"find this phrase\" | \"or this one\")").withName(getName())
        );
        if(allowSynonyms) {
            tag = tag.with(br(),
                    label("Synonym Search? ").attr("title", "Allows synonyms of keywords to be matched.").with(
                            input().withType("checkbox").withId(getUseSynonymsId()).withName(getUseSynonymsId()).withValue("on")
                    )
            );
        }
        return tag;
    }


    @Override
    public boolean contributesToScore() { return Constants.SIMILARITY_ATTRIBUTE_CLASSES.stream().anyMatch(clazz->clazz.isInstance(attribute)); }
}
