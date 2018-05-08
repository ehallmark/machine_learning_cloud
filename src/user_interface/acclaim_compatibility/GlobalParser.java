package user_interface.acclaim_compatibility;

import data_pipeline.helpers.Function3;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.*;
import org.nd4j.linalg.primitives.Pair;
import seeding.google.elasticsearch.Attributes;
import seeding.google.elasticsearch.attributes.CalculatedExpirationDate;
import seeding.google.elasticsearch.attributes.CalculatedPriorityDate;
import seeding.google.elasticsearch.attributes.Expired;
import user_interface.ui_models.filters.AbstractBetweenFilter;
import user_interface.ui_models.filters.AbstractBooleanIncludeFilter;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by ehallmark on 1/5/18.
 */
public class GlobalParser {
    public static final Map<String,Function3<String,String,String,QueryBuilder>> transformationsForAttr;
    private static final Function3<String,String,String,QueryBuilder> defaultTransformation;
    private static final Map<String,Float> defaultFields = Collections.synchronizedMap(new HashMap<>());
    static {
        defaultFields.put(Attributes.ABSTRACT,1f);
        defaultFields.put(Attributes.INVENTION_TITLE,1f);
        defaultFields.put(Attributes.CLAIMS,1f);
        defaultFields.put(Attributes.LATEST_ASSIGNEES+"."+Attributes.LATEST_ASSIGNEE,1f);
        defaultFields.put(Attributes.ASSIGNEES+"."+Attributes.ASSIGNEE_HARMONIZED,1f);
        defaultFields.put(Attributes.CODE,1f);
        defaultFields.put(Attributes.TECHNOLOGY,1f);

        defaultTransformation = (name,val,user) -> {
            if(name!=null && name.length()>0) {
                if(name.endsWith("Date")||name.endsWith("_date")||name.equals("date")) {
                    // try date formatting
                    val = tryCoerceDate(val);
                }
                return QueryBuilders.queryStringQuery(name + ":" + val.toLowerCase()).defaultOperator(Operator.AND);
            } else {
                QueryBuilder query = QueryBuilders.queryStringQuery(val.toLowerCase()).fields(defaultFields).defaultOperator(Operator.AND);
                return QueryBuilders.boolQuery()
                        .must(query);
            }
        };
        transformationsForAttr = Collections.synchronizedMap(new HashMap<>());
        transformationsForAttr.put(Attributes.COUNTRY_CODE,(name,str,user)->QueryBuilders.termQuery(name,str.toUpperCase()));
        transformationsForAttr.put(Attributes.KIND_CODE,(name,str,user) ->{
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
        transformationsForAttr.put(Attributes.EXPIRATION_DATE_ESTIMATED,(name,val,user)->{
            if(val.equals("expired")) {
                return new AbstractBooleanIncludeFilter(new Expired(), AbstractFilter.FilterType.BoolTrue).getFilterQuery();
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
                AbstractBetweenFilter betweenFilter = new AbstractBetweenFilter(new CalculatedExpirationDate(), AbstractFilter.FilterType.Between);
                betweenFilter.setMin(date1);
                betweenFilter.setMax(date2);
                return betweenFilter.getScriptFilter();
            }
            return null;
        });
        transformationsForAttr.put(Attributes.PRIORITY_DATE_ESTIMATED,(name,val,user)->{
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
                AbstractBetweenFilter betweenFilter = new AbstractBetweenFilter(new CalculatedPriorityDate(), AbstractFilter.FilterType.Between);
                betweenFilter.setMin(date1);
                betweenFilter.setMax(date2);
                return betweenFilter.getScriptFilter();
            }
            return null;
        });
        transformationsForAttr.put("FIELD",(name,val,user)->{
            String replace;
            if(val.startsWith("isEmpty")) {
                replace="isEmpty";
            } else if(val.startsWith("isNotEmpty")) {
                replace="isNotEmpty";
            } else {
                replace=null;
            }

            if(replace!=null) {
                String field = val.replaceFirst(replace, "").toUpperCase();
                String attr = Attributes.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(field, field.length() > 2 && field.endsWith("_F") ? Attributes.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(field.substring(0, field.length() - 2)) : null);
                if (attr != null) {
                    // check for nested)
                    QueryBuilder queryBuilder = QueryBuilders.existsQuery(attr);

                    String root = attr.contains(".") ? attr.substring(0, attr.indexOf(".")) : attr;
                    if (Attributes.NESTED_ATTRIBUTES.contains(root)) {
                        queryBuilder = QueryBuilders.nestedQuery(root, queryBuilder, ScoreMode.Min);
                    }

                    if(replace.equals("isEmpty")) {
                        return QueryBuilders.boolQuery().mustNot(queryBuilder);
                    } else {
                        return QueryBuilders.boolQuery().must(queryBuilder);
                    }
                }
            }
            return null;
        });
        transformationsForAttr.put("ICLM",(name,val,user)->{
            String attrName = Attributes.CLAIMS;
            return QueryBuilders.queryStringQuery(val).field(attrName).analyzeWildcard(true).defaultOperator(Operator.AND);
        });
        transformationsForAttr.put("DCLM",(name,val,user)->{
            String attrName = Attributes.CLAIMS;
            return QueryBuilders.queryStringQuery(val).field(attrName).analyzeWildcard(true).defaultOperator(Operator.AND);
        });
        transformationsForAttr.put("TAC",(name,val,user)->{
            return QueryBuilders.queryStringQuery(val).field(Attributes.ABSTRACT).field(Attributes.INVENTION_TITLE).field(Attributes.CLAIMS).analyzeWildcard(true).defaultOperator(Operator.AND);
        });
        transformationsForAttr.put(Attributes.CODE, (name,val,user)->{
            val = val.trim();
            if(val.endsWith("+")) {
                if(val.length()>1) {
                    val = val.substring(0,val.length()-1);
                    return QueryBuilders.termQuery(Attributes.TREE,val);
                } else {
                    throw new RuntimeException("Must specify a CPC Prefix before the '+' in the Expert Query Filter.");
                }
            } else {
                return QueryBuilders.queryStringQuery(name+":"+val).defaultOperator(Operator.AND);
            }
        });

    }

    private static String tryCoerceDate(String val) {
        try {
            val = LocalDate.parse(val, DateTimeFormatter.ofPattern("MM/dd/yyyy")).format(DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            try {
                val = LocalDate.parse(val, DateTimeFormatter.ofPattern("yyyy/MM/dd")).format(DateTimeFormatter.ISO_DATE);
            } catch (Exception e2) {
                if(val.length()==4)val = LocalDate.of(Integer.valueOf(val),1,1).format(DateTimeFormatter.ISO_DATE);
            }
        }
        return val;
    }

    private QueryParser parser;
    private String user;
    public GlobalParser(String user) {
        this.user=user;
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

    private static QueryBuilder replaceAcclaimName(String queryStr, Query query, String user) {
        String nestedPath = null;
        String fullAttr = null;
        String val = null;
        int colIdx = queryStr.indexOf(":");
        if(colIdx>0) {
            String prefix = queryStr.substring(0,colIdx);
            System.out.println("Prefix: "+prefix);
            System.out.println("Querystr: "+queryStr);
            String attr = Attributes.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(prefix, prefix.endsWith("_F")&&prefix.length()>2 ? Attributes.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(prefix.substring(0,prefix.length()-2)):null);
            if(attr!=null && prefix.equals(prefix.toUpperCase())&&queryStr.length()>colIdx+1) {
                System.out.println("Found attr: "+attr);
                fullAttr = attr;
                val = queryStr.substring(colIdx+1);
                // check filing
                if(attr.contains(".")) attr = attr.substring(0,attr.indexOf("."));
                if(Attributes.NESTED_ATTRIBUTES.contains(attr)) {
                    nestedPath = attr;
                }

                queryStr = fullAttr+":"+val;

            } else if(transformationsForAttr.containsKey(prefix)&&queryStr.length()>colIdx+1) {
                System.out.println("Found prefix: "+prefix);
                fullAttr = prefix;
                val = queryStr.substring(colIdx+1);
            } else {
                // warning
                System.out.println("Warning...");
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

                    firstVal = tryCoerceDate(firstVal);
                    secondVal = tryCoerceDate(secondVal);

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

        if(val==null) val = queryStr;

        if(query instanceof TermQuery) {
            if(val.replaceAll("[^A-Za-z0-9]","").length()!=val.length()) {
                val = "\"" + val + "\"";
            }
        }

        // check for transformation
        if(fullAttr!=null) {
            Function3<String, String, String, QueryBuilder> builder = transformationsForAttr.getOrDefault(fullAttr, defaultTransformation);
            strQuery = builder.apply(fullAttr,val,user);
        } else {
            strQuery = defaultTransformation.apply(null,val,user);
        }


        //if(queryStr.equals("near")||queryStr.equals("+near")) {
        //    System.out.println("Foound near!!!!!");
        //}
        if(nestedPath!=null && strQuery!=null && ! (strQuery instanceof NestedQueryBuilder)) {
            strQuery = QueryBuilders.nestedQuery(nestedPath,strQuery, ScoreMode.Max);
        }
        return strQuery;
    }

    public QueryBuilder parseAcclaimQuery(String text) {
        if(text==null || text.isEmpty()) return null;
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
            return replaceAcclaimName(query.toString(),query,user);
        }
    }

    private Pair<SpanQueryBuilder,String> spanQueryFrom(BooleanClause clause, String defaultField) {
        Query query = clause.getQuery();
        if(query instanceof BooleanQuery) {
            SpanOrQueryBuilder orBuilder = null;
            SpanContainingQueryBuilder andBuilder = null;

            SpanQueryBuilder lastInner = null;
            boolean useAnd = ((BooleanQuery) query).clauses().stream().findFirst().orElseThrow(()->new RuntimeException("Parse Error: No boolean clause found in NEAR or ADJ search.")).isRequired();
            String nestedAttr = null;
            for(int i = 0; i < ((BooleanQuery) query).clauses().size(); i++) {
                BooleanClause c = ((BooleanQuery) query).clauses().get(i);
                Pair<SpanQueryBuilder,String> inner = spanQueryFrom(c,defaultField);
                if(inner!=null) {
                    nestedAttr = inner.getSecond();
                    if(c.isProhibited()) {
                        throw new RuntimeException("Parse Error: Cannot use NOT with NEAR or ADJ.");
                    } else {
                        if(useAnd) {
                            if(andBuilder==null) {
                                if(lastInner!=null) {
                                    andBuilder = new SpanContainingQueryBuilder(lastInner, inner.getFirst());
                                }
                            } else {
                                andBuilder = new SpanContainingQueryBuilder(andBuilder,inner.getFirst());
                            }
                        } else {
                            if (orBuilder == null) {
                                orBuilder = new SpanOrQueryBuilder(inner.getFirst());
                            } else {
                                orBuilder = orBuilder.addClause(inner.getFirst());
                            }
                        }
                    }
                    lastInner = inner.getFirst();
                }
            }
            if(useAnd) return new Pair<>(andBuilder,nestedAttr);
            else return new Pair<>(orBuilder,nestedAttr);
        } else {
            if(defaultField!=null) {
                // check nested
                String nestedAttr = null;
                if(Attributes.NESTED_ATTRIBUTES.contains(defaultField)||(defaultField.contains(".")&&Attributes.NESTED_ATTRIBUTES.contains(defaultField.substring(0,defaultField.indexOf("."))))) {
                    nestedAttr = defaultField.substring(0,defaultField.indexOf("."));
                }
                String queryStr = query.toString();
                if(queryStr.startsWith("TAC:")||queryStr.startsWith("ICLM:")||queryStr.startsWith("DCLM:")) {
                    queryStr = queryStr.substring(queryStr.indexOf(":")+1);
                }
                return new Pair<>(new SpanTermQueryBuilder(defaultField, queryStr),nestedAttr);
            } else {
                String[] fields = query.toString().split(":",2);
                String field = fields[0];
                if(fields.length>1) {
                    String attr = Attributes.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(field, field.length() > 2 && field.endsWith("_F") ? Attributes.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(field.substring(0, field.length() - 2)) : null);
                    if(attr!=null) {
                        String val = fields[1];
                        // check nested
                        String nestedAttr = null;
                        if(Attributes.NESTED_ATTRIBUTES.contains(attr)||(attr.contains(".")&&Attributes.NESTED_ATTRIBUTES.contains(attr.substring(0,attr.indexOf("."))))) {
                            nestedAttr = attr.substring(0,attr.indexOf("."));
                        }
                        return new Pair<>(new SpanTermQueryBuilder(attr, val),nestedAttr);
                    }
                }
            }
            return null;
        }
    }

    public QueryBuilder parseAcclaimQueryHelper(BooleanQuery booleanQuery) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        for(int i = 0; i < booleanQuery.clauses().size(); i++) {
            BooleanClause c = booleanQuery.clauses().get(i);
            Query subQuery = c.getQuery();
            String queryStr = subQuery.toString();

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
                QueryBuilder builder;
                Integer preIdx = i > 0 ? i-1 : null;
                Integer postIdx = i < booleanQuery.clauses().size()-1 ? i+1 : null;

                boolean isProximityQuery = false;
                String queryStrEnd = queryStr;
                if(queryStrEnd.contains(":")&&queryStrEnd.length()>queryStrEnd.indexOf(":")+1) {
                    queryStrEnd = queryStrEnd.substring(queryStrEnd.indexOf(":")+1);
                }
                String slopStr = "";
                while(queryStrEnd.length()>0&&Character.isDigit(queryStrEnd.charAt(queryStrEnd.length()-1))) {
                    slopStr = queryStrEnd.substring(queryStrEnd.length()-1) + slopStr;
                    queryStrEnd = queryStrEnd.substring(0,queryStrEnd.length()-1);
                }
                if((queryStrEnd.equals("NEAR") || queryStrEnd.equals("ADJ")) && !queryStrEnd.contains(" ") && !queryStrEnd.contains(":") && preIdx!=null&&postIdx!=null) {
                    isProximityQuery = true;
                    int slop;
                    boolean useOrder = queryStrEnd.startsWith("ADJ");
                    try {
                        slop = Integer.valueOf(slopStr);
                    } catch(Exception e) {
                        slop = 1;
                    }
                    if(slop<=0) slop = 1;
                    // valid near query
                    boolean matchAll = false;
                    boolean matchTAC = false;
                    boolean matchICLM = false;
                    boolean matchDCLM = false;
                    String preQueryStr = booleanQuery.clauses().get(preIdx).getQuery().toString();
                    if(!preQueryStr.contains(":")) {
                        matchAll = true;
                    } else if(preQueryStr.startsWith("TAC:")) {
                        matchTAC = true;
                    } else if(preQueryStr.startsWith("ICLM:")) {
                        matchICLM = true;
                    } else if(preQueryStr.startsWith("DCLM:")) {
                        matchDCLM=true;
                    }

                    if(matchAll||matchTAC||matchICLM||matchDCLM) {
                        Collection<String> fields;
                        if(matchAll) {
                            fields = defaultFields.keySet();
                        } else {
                            if(matchTAC) {
                                // TAC
                                fields = Arrays.asList(Attributes.CLAIMS, Attributes.INVENTION_TITLE, Attributes.ABSTRACT);
                            } else {
                                fields = Collections.singleton(Attributes.CLAIMS);
                            }
                        }
                        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                        for(String field : fields) {
                            Pair<SpanQueryBuilder,String> builder1 = spanQueryFrom(booleanQuery.clauses().get(preIdx),field);
                            Pair<SpanQueryBuilder,String> builder2 = spanQueryFrom(booleanQuery.clauses().get(postIdx),field);
                            QueryBuilder innerQuery =  new SpanNearQueryBuilder(builder1.getFirst(), slop).inOrder(useOrder)
                                    .addClause(builder2.getFirst());

                            if(builder1.getSecond()!=null) {
                                innerQuery = QueryBuilders.nestedQuery(builder1.getSecond(),innerQuery,ScoreMode.Max);
                            }
                            boolQueryBuilder = boolQueryBuilder.should(innerQuery);
                        }
                        builder = boolQueryBuilder;
                    } else {
                        Pair<SpanQueryBuilder,String> builder1 = spanQueryFrom(booleanQuery.clauses().get(preIdx),null);
                        Pair<SpanQueryBuilder,String> builder2 = spanQueryFrom(booleanQuery.clauses().get(postIdx),null);
                        if(builder1!=null&&builder2!=null) {
                            builder = new SpanNearQueryBuilder(builder1.getFirst(), slop).inOrder(useOrder)
                                    .addClause(builder2.getFirst());
                            if(builder1.getSecond()!=null) {
                                builder = QueryBuilders.nestedQuery(builder1.getSecond(),builder,ScoreMode.Max);
                            }
                        } else {
                            builder = null;
                        }
                    }

                } else {
                    builder = replaceAcclaimName(queryStr,subQuery,user);
                }
                if(builder!=null) {
                    if(isProximityQuery||c.isRequired()) {
                        boolQuery = boolQuery.must(builder);
                    } else if (c.isProhibited()) {
                        boolQuery = boolQuery.mustNot(builder);
                    } else {
                        boolQuery = boolQuery.should(builder);
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
        GlobalParser parser = new GlobalParser("ehallmark");

        QueryBuilder res = parser.parseAcclaimQuery("RFID:id.somethin && blah near2 blah \"search everything\" CPC:A02F33+ AND CPC:A02301\\/32 (ANC_F:\"HTC CORP\" OR ANC_F:\"HTC\") AND (ANO_F:HTC || FIELD:isEmptyANO_F) AND TTL:one ADJ21 (TTL: three AND TTL:two) CC:US AND DT:G AND EXP:[NOW+5YEARS TO NOW+6YEARS] AND EXP:f AND NOT PEND:false AND (PT:U OR PT:RE)");

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
