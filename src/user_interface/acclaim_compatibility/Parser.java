package user_interface.acclaim_compatibility;

import elasticsearch.DataIngester;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.join.ScoreMode;
import org.deeplearning4j.berkeley.Pair;
import org.elasticsearch.index.query.*;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import seeding.Constants;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by ehallmark on 1/5/18.
 */
public class Parser {
    private QueryParser parser;
    public Parser() {
        this.parser = new QueryParser("", new EnglishAnalyzer());
        parser.setDefaultOperator(QueryParser.Operator.AND);
    }

    public Map<String,Object> parse(String text) throws ParseException {
        return parseHelper(parser.parse(text));
    }

    private Map<String,Object> parseHelper(Query query) {
        Map<String,Object> map = new HashMap<>();
        if(query instanceof BooleanQuery) {
            List<BooleanClause> clauses = ((BooleanQuery) query).clauses();
            for(int i = 0; i < clauses.size(); i++) {
                map.put(query.getClass().getSimpleName()+i, parseHelper(clauses.get(i).getQuery()));
            }
        } else {
            map.put(query.getClass().getSimpleName(),query.toString());
        }
        return map;
    }

    private static String yearSyntaxToDate(String val, String t) {
        int operatorIdx = Math.max(val.lastIndexOf("-"),val.lastIndexOf("+"));
        if(operatorIdx>0&&operatorIdx<val.length()-1) {
            String num = val.substring(operatorIdx).replace(t+"s","").replace(t, "").trim();
            String first = val.substring(0,operatorIdx);
            try {
                LocalDate date = LocalDate.parse(first, DateTimeFormatter.ISO_DATE);
                int n = Integer.valueOf(num);
                if(t.startsWith("year")) {
                    date = date.plusYears(n);
                } else if (t.startsWith("month")) {
                    date = date.plusMonths(n);
                } else if(t.startsWith("day")) {
                    date = date.plusDays(n);
                }
                val = date.toString();
            } catch(Exception e) {

            }
        }
        return val;
    }

    private static Pair<QueryBuilder,Boolean> replaceAcclaimName(String queryStr, Query query) {
        boolean isFiling = false;
        String nestedPath = null;
        int colIdx = queryStr.indexOf(":");
        if(colIdx>0) {
            String prefix = queryStr.substring(0,colIdx);
            String attr = Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(prefix, prefix.endsWith("_F")&&prefix.length()>2 ? Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(prefix.substring(0,prefix.length()-2)):null);
            if(attr!=null && prefix.equals(prefix.toUpperCase())) {
                queryStr = attr+queryStr.substring(colIdx);
                // check filing
                if(attr.contains(".")) attr = attr.substring(0,attr.indexOf("."));
                if(Constants.FILING_ATTRIBUTES_SET.contains(attr)) {
                    isFiling = true;
                }
                if(Constants.NESTED_ATTRIBUTES.contains(attr)) {
                    nestedPath = attr;
                }
            } else {
                // warning
            }
        }
        // check date
        if(query instanceof TermRangeQuery) {
            TermRangeQuery numericRangeQuery = (TermRangeQuery) query;
            queryStr = queryStr.replace("now", LocalDate.now().toString());
            int s = queryStr.indexOf(" TO ");
            if(queryStr.length()-1>s+4) {
                int r = Math.max(queryStr.indexOf("["),queryStr.indexOf("{"));
                if(r>=0&&queryStr.length()>r+1) {
                    String firstVal = queryStr.substring(r+1, s).trim();
                    String secondVal = queryStr.substring(s + 4, queryStr.length()-1).trim();
                    try {
                        firstVal = LocalDate.parse(firstVal, DateTimeFormatter.ofPattern("MM/dd/yyyy")).format(DateTimeFormatter.ISO_DATE);
                    } catch (Exception e) {
                        try {
                            firstVal = LocalDate.parse(firstVal, DateTimeFormatter.ofPattern("yyyy/MM/dd")).format(DateTimeFormatter.ISO_DATE);
                        } catch (Exception e2) {

                        }
                    }
                    try {
                        secondVal = LocalDate.parse(secondVal, DateTimeFormatter.ofPattern("MM/dd/yyyy")).format(DateTimeFormatter.ISO_DATE);
                    } catch (Exception e) {
                        try {
                            secondVal = LocalDate.parse(secondVal, DateTimeFormatter.ofPattern("yyyy/MM/dd")).format(DateTimeFormatter.ISO_DATE);
                        } catch (Exception e2) {

                        }
                    }
                    if(secondVal.contains("year")) {
                        secondVal = yearSyntaxToDate(secondVal,"year");
                    } else if(secondVal.contains("month")) {
                        secondVal = yearSyntaxToDate(secondVal,"month");
                    } else if(secondVal.contains("day")) {
                        secondVal = yearSyntaxToDate(secondVal,"day");
                    }
                    if(firstVal.contains("year")) {
                        firstVal = yearSyntaxToDate(firstVal,"year");
                    } else if(firstVal.contains("month")) {
                        firstVal = yearSyntaxToDate(firstVal,"month");
                    } else if(firstVal.contains("day")) {
                        firstVal = yearSyntaxToDate(firstVal,"day");
                    }
                    queryStr = queryStr.substring(0, r+1) + firstVal + " TO " + secondVal + queryStr.substring(queryStr.length() - 1, queryStr.length());

                }
            }
        }
        QueryBuilder strQuery = QueryBuilders.queryStringQuery(queryStr).defaultOperator(Operator.AND);

        //if(queryStr.equals("near")||queryStr.equals("+near")) {
        //    System.out.println("Foound near!!!!!");
        //}
        if(nestedPath!=null) {
            strQuery = QueryBuilders.nestedQuery(nestedPath,strQuery, ScoreMode.Max);
        }
        return new Pair<>(strQuery,isFiling);
    }

    public QueryBuilder parseAcclaimQuery(String text) {
        Query query;
        try {
            query = parser.parse(text.replace(" to "," TO "));
        } catch(Exception e) {
            throw new RuntimeException("Parse error: "+e.getMessage());
        }
        //boolean needParens = this.getMinimumNumberShouldMatch() > 0;
        //if(needParens) {
        //    buffer.append("(");
        //}

        BooleanQuery booleanQuery;
        if(query instanceof BooleanQuery) {
            booleanQuery = (BooleanQuery)query;
            return parseAcclaimQueryHelper(booleanQuery);
        } else {
            Pair<QueryBuilder,Boolean> p = replaceAcclaimName(query.toString(),query);
            boolean isFiling = p.getSecond();
            if(isFiling) {
                return new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME,p.getFirst(),false);
            } else {
               return p.getFirst();
            }
        }
    }

    public QueryBuilder parseAcclaimQueryHelper(BooleanQuery booleanQuery) {
        boolean currOr;
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().minimumShouldMatch(1);
        for(int i = 0; i < booleanQuery.clauses().size(); i++) {
            BooleanClause c = booleanQuery.clauses().get(i);
            Query subQuery = c.getQuery();
            currOr = (!c.isRequired()&&!c.isProhibited());
            if(subQuery instanceof BooleanQuery) {
                QueryBuilder query = parseAcclaimQueryHelper((BooleanQuery)subQuery);
                if (query != null) {
                    if (c.isProhibited()) {
                        boolQuery = boolQuery.mustNot(query);
                    } else if(c.isRequired()) {
                        boolQuery = boolQuery.must(query);
                    } else {
                        boolQuery = boolQuery.should(query);
                    }
                }
            } else {
                String queryStr = subQuery.toString();
                Pair<QueryBuilder,Boolean> p = replaceAcclaimName(queryStr,subQuery);
                QueryBuilder builder = p.getFirst();
                if(builder!=null) {
                    if (p.getSecond()) {
                        builder = new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, builder, false);
                    }
                    if (p.getFirst() != null) {
                        if (c.isProhibited()) {
                            boolQuery = boolQuery.mustNot(builder);
                        } else if(c.isRequired()) {
                            boolQuery = boolQuery.must(builder);
                        } else {
                            boolQuery = boolQuery.should(builder);
                        }
                    }
                }
            }
        }

        //if(needParens) {
        //    buffer.append(")");
        //}

        //if(this.getMinimumNumberShouldMatch() > 0) {
        //    buffer.append('~');
        //    buffer.append(this.getMinimumNumberShouldMatch());
        //}

        return boolQuery;
    }



    public static void main(String[] args) throws Exception {
        Parser parser = new Parser();

        QueryBuilder res = parser.parseAcclaimQuery("(TTL:foo* NEAR foot OR PRIRD:[NOW-2000DAYS TO NOW+2YEARS] (something && ACLM:(else OR elephant && \"phrase of something\"))) OR -field2:bar AND NOT foorbar");

        System.out.println(" query: "+res);
    }


    public static void printHelper(Map<String,Object> res, String... parents) {
        res.entrySet().stream().sorted(Comparator.comparing(e->e.getKey())).forEach((e)->{
            String k = e.getKey();
            Object v = e.getValue();
            for(int j = 0; j < parents.length; j++) { System.out.print("   "); }
            if(v instanceof Map) {
                System.out.println(k);
                String[] newParents = new String[parents.length+1];
                for(int i = 0; i < parents.length; i++) {
                    newParents[i]=parents[i];
                }
                newParents[parents.length]=k;
                printHelper((Map<String,Object>)v,newParents);
            } else {
                System.out.println(k+": "+v);
            }
        });
    }
}
