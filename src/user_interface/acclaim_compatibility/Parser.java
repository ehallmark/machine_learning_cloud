package user_interface.acclaim_compatibility;

import data_pipeline.helpers.Function2;
import elasticsearch.DataIngester;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.join.ScoreMode;
import org.deeplearning4j.berkeley.Pair;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.index.query.*;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import seeding.Constants;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.attributes.script_attributes.CalculatedExpirationDateAttribute;
import user_interface.ui_models.attributes.script_attributes.CalculatedPriorityDateAttribute;
import user_interface.ui_models.attributes.script_attributes.ExpiredAttribute;
import user_interface.ui_models.filters.AbstractBetweenFilter;
import user_interface.ui_models.filters.AbstractBooleanExcludeFilter;
import user_interface.ui_models.filters.AbstractBooleanIncludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created by ehallmark on 1/5/18.
 */
public class Parser {
    private static final Map<String,Function2<String,String,QueryBuilder>> transformationsForAttr;
    private static final Function2<String,String,QueryBuilder> defaultTransformation;
    static {
        defaultTransformation = (name,val) -> {
            if(name!=null && name.length()>0) {
                return QueryBuilders.queryStringQuery(name + ":" + val).defaultOperator(Operator.AND);
            } else {
                return QueryBuilders.queryStringQuery(val).defaultOperator(Operator.AND);
            }
        };
        transformationsForAttr = Collections.synchronizedMap(new HashMap<>());
        transformationsForAttr.put(Constants.FILING_COUNTRY,(name,str)->QueryBuilders.termQuery(name,str.toUpperCase()));
        transformationsForAttr.put(Constants.DOC_TYPE,(name,str)->{
            String ret;
            if(str.toLowerCase().equals("g")) ret = "patents";
            else if(str.toLowerCase().equals("a")) ret = "applications";
            else ret = str;
            return QueryBuilders.termQuery(name,ret);
        });
        transformationsForAttr.put(Constants.DOC_KIND,(name,str) ->{
            QueryBuilder ret;
            str=str.toUpperCase();
            if(str.equals("U")) ret = QueryBuilders.termsQuery(name,"B1","B","B2");
            else if(str.equals("A")) ret =QueryBuilders.termsQuery(name,"A1","A","A2","A9");
            else if(str.equals("P")) ret = QueryBuilders.termsQuery(name,"P","PP","P1","P2","P3","P4","P9");
            else if(str.equals("H")) ret = QueryBuilders.termsQuery(name,"H");
            else if(str.equals("D")) ret = QueryBuilders.termQuery(name,"S");
            else if(str.equals("RE")) ret = QueryBuilders.termQuery(name,"E");
            else ret = QueryBuilders.queryStringQuery(name+":"+str).defaultOperator(Operator.AND);
            return ret;
        });
        transformationsForAttr.put(Constants.EXPIRATION_DATE,(name,val)->{
            if(val.equals("expired")) {
                return new AbstractBooleanIncludeFilter(new ExpiredAttribute(), AbstractFilter.FilterType.BoolTrue).getFilterQuery();
            }
            if(val.length()>2) {
                String[] vals = val.substring(1, val.length() - 1).split(" TO ");
                LocalDate date1= null;
                LocalDate date2= null;
                try {
                    date1 = LocalDate.parse(vals[0], DateTimeFormatter.ISO_DATE);
                } catch (Exception e) {

                }
                try {
                    date2 = LocalDate.parse(vals[1], DateTimeFormatter.ISO_DATE);
                } catch(Exception e) {

                }
                AbstractBetweenFilter betweenFilter = new AbstractBetweenFilter(new CalculatedExpirationDateAttribute(), AbstractFilter.FilterType.Between);
                betweenFilter.setMin(date1);
                betweenFilter.setMax(date2);
                return betweenFilter.getScriptFilter();
            }
            return null;
        });
        transformationsForAttr.put(Constants.PRIORITY_DATE,(name,val)->{
            if(val.length()>2) {
                String[] vals = val.substring(1, val.length() - 1).split(" TO ");
                LocalDate date1= null;
                LocalDate date2= null;
                try {
                    date1 = LocalDate.parse(vals[0], DateTimeFormatter.ISO_DATE);
                } catch (Exception e) {

                }
                try {
                    date2 = LocalDate.parse(vals[1], DateTimeFormatter.ISO_DATE);
                } catch(Exception e) {

                }
                AbstractBetweenFilter betweenFilter = new AbstractBetweenFilter(new CalculatedPriorityDateAttribute(), AbstractFilter.FilterType.Between);
                betweenFilter.setMin(date1);
                betweenFilter.setMax(date2);
                return betweenFilter.getScriptFilter();
            }
            return null;
        });
        transformationsForAttr.put("FIELD",(name,val)->{
            if(val.startsWith("isEmpty")) {
                String field = val.replaceFirst("isEmpty","").toUpperCase();
                String attr = Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(field, field.length()>2&&field.endsWith("_F")?Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(field.substring(0,field.length()-2)):null);
                if(attr!=null) {
                    // check for nested)
                    QueryBuilder queryBuilder = QueryBuilders.existsQuery(attr);

                    String root = attr.contains(".") ? attr.substring(0,attr.indexOf(".")) : attr;
                    if(Constants.NESTED_ATTRIBUTES.contains(root)) {
                        queryBuilder = QueryBuilders.nestedQuery(root,queryBuilder,ScoreMode.Min);
                    }

                    // check filing
                    if(Constants.FILING_ATTRIBUTES_SET.contains(root)) {
                        queryBuilder = new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, queryBuilder, false);
                    }
                    return QueryBuilders.boolQuery().mustNot(queryBuilder);
                }
            };
            return null;
        });
        transformationsForAttr.put("PEND",(name,val)->{
            if(val.toLowerCase().startsWith("t")) {
                return QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.applications.toString()))
                        .must(QueryBuilders.termQuery(Constants.GRANTED, false));
            } else {
                return QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.applications.toString()))
                        .must(QueryBuilders.termQuery(Constants.GRANTED, true));
            }
        });
    }

    private QueryParser parser;
    public Parser() {
        this.parser = new QueryParser("", new KeywordAnalyzer());
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
                if(t.toLowerCase().startsWith("year")) {
                    date = date.plusYears(n);
                } else if (t.toLowerCase().startsWith("month")) {
                    date = date.plusMonths(n);
                } else if(t.toLowerCase().startsWith("day")) {
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
        String fullAttr = null;
        String val = null;
        int colIdx = queryStr.indexOf(":");
        if(colIdx>0) {
            String prefix = queryStr.substring(0,colIdx);
            String attr = Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(prefix, prefix.endsWith("_F")&&prefix.length()>2 ? Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(prefix.substring(0,prefix.length()-2)):null);
            if(attr!=null && prefix.equals(prefix.toUpperCase())&&queryStr.length()>colIdx+1) {
                fullAttr = attr;
                val = queryStr.substring(colIdx+1);
                // check filing
                if(attr.contains(".")) attr = attr.substring(0,attr.indexOf("."));
                if(Constants.FILING_ATTRIBUTES_SET.contains(attr)) {
                    isFiling = true;
                }
                if(Constants.NESTED_ATTRIBUTES.contains(attr)) {
                    nestedPath = attr;
                }

                queryStr = fullAttr+":"+val;

            } else if(transformationsForAttr.containsKey(prefix)&&queryStr.length()>colIdx+1) {
                fullAttr = prefix;
                val = queryStr.substring(colIdx+1);
            } else {
                // warning
            }
        }

        // check date
        if(query instanceof TermRangeQuery) {
            TermRangeQuery numericRangeQuery = (TermRangeQuery) query;
            int s = queryStr.indexOf(" TO ");
            if(queryStr.length()-1>s+4) {
                int r = Math.max(queryStr.indexOf("["),queryStr.indexOf("{"));
                if(r>=0&&queryStr.length()>r+1) {
                    String d = queryStr.substring(r).replace("now", LocalDate.now().toString()).replace("NOW",LocalDate.now().toString());
                    s = d.indexOf(" TO ");
                    String firstVal = d.substring(1, s).trim();
                    String secondVal = d.substring(s + 4, d.length()-1).trim();
                    try {
                        firstVal = LocalDate.parse(firstVal, DateTimeFormatter.ofPattern("MM/dd/yyyy")).format(DateTimeFormatter.ISO_DATE);
                    } catch (Exception e) {
                        try {
                            firstVal = LocalDate.parse(firstVal, DateTimeFormatter.ofPattern("yyyy/MM/dd")).format(DateTimeFormatter.ISO_DATE);
                        } catch (Exception e2) {
                            if(firstVal.length()==4)firstVal = LocalDate.of(Integer.valueOf(firstVal),1,1).format(DateTimeFormatter.ISO_DATE);
                        }
                    }
                    try {
                        secondVal = LocalDate.parse(secondVal, DateTimeFormatter.ofPattern("MM/dd/yyyy")).format(DateTimeFormatter.ISO_DATE);
                    } catch (Exception e) {
                        try {
                            secondVal = LocalDate.parse(secondVal, DateTimeFormatter.ofPattern("yyyy/MM/dd")).format(DateTimeFormatter.ISO_DATE);
                        } catch (Exception e2) {
                            if(secondVal.length()==4)secondVal = LocalDate.of(Integer.valueOf(secondVal),1,1).format(DateTimeFormatter.ISO_DATE);
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

                    val = d.substring(0, 1) + firstVal + " TO " + secondVal + d.substring(d.length() - 1, d.length());
                    if(fullAttr==null) {
                        queryStr = val;
                    } else {
                        queryStr = fullAttr + ":" + val;
                    }

                }
            }
        }

        QueryBuilder strQuery;

        // check for transformation
        if(fullAttr!=null) {
            Function2<String, String, QueryBuilder> builder = transformationsForAttr.getOrDefault(fullAttr, defaultTransformation);
            strQuery = builder.apply(fullAttr,val);
        } else {
            strQuery = QueryBuilders.queryStringQuery(queryStr).defaultOperator(Operator.AND);
        }


        //if(queryStr.equals("near")||queryStr.equals("+near")) {
        //    System.out.println("Foound near!!!!!");
        //}
        if(nestedPath!=null && strQuery!=null) {
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
            if(isFiling&&p.getFirst()!=null) {
                return new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME,p.getFirst(),false);
            } else {
               return p.getFirst();
            }
        }
    }

    public QueryBuilder parseAcclaimQueryHelper(BooleanQuery booleanQuery) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        for(int i = 0; i < booleanQuery.clauses().size(); i++) {
            BooleanClause c = booleanQuery.clauses().get(i);
            Query subQuery = c.getQuery();
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

        QueryBuilder res = parser.parseAcclaimQuery("(ANC_F:\"HTC CORP\" OR ANC_F:\"HTC\") AND (ANO_F:HTC || FIELD:isEmptyANO_F) AND CC:US AND DT:G AND EXP:[NOW+5YEARS TO NOW+6YEARS] AND EXP:f AND NOT PEND:false AND (PT:U OR PT:RE)");

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
