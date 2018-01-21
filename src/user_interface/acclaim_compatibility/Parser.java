package user_interface.acclaim_compatibility;

import data_pipeline.helpers.Function3;
import elasticsearch.DatasetIndex;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.*;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.attributes.dataset_lookup.TermsLookupAttribute;
import user_interface.ui_models.attributes.script_attributes.CalculatedExpirationDateAttribute;
import user_interface.ui_models.attributes.script_attributes.CalculatedPriorityDateAttribute;
import user_interface.ui_models.attributes.script_attributes.ExpiredAttribute;
import user_interface.ui_models.filters.AbstractBetweenFilter;
import user_interface.ui_models.filters.AbstractBooleanIncludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by ehallmark on 1/5/18.
 */
public class Parser {
    public static final Map<String,Function3<String,String,String,QueryBuilder>> transformationsForAttr;
    private static final Function3<String,String,String,QueryBuilder> defaultTransformation;
    private static final Map<String,Float> defaultFields = Collections.synchronizedMap(new HashMap<>());
    static {
        defaultFields.put(Constants.ABSTRACT,1f);
        defaultFields.put(Constants.INVENTION_TITLE,1f);
        defaultFields.put(Constants.CLAIMS+"."+Constants.CLAIM,1f);
        defaultFields.put(Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE,1f);
        defaultFields.put(Constants.LATEST_ASSIGNEE+"."+Constants.NORMALIZED_LATEST_ASSIGNEE,1f);
        defaultFields.put(Constants.ASSIGNEES+"."+Constants.ASSIGNEE,1f);
        defaultFields.put(Constants.CPC_CODES,1f);
        defaultFields.put(Constants.TECHNOLOGY,1f);
        defaultFields.put(Constants.NESTED_CPC_CODES+"."+Constants.CPC_TITLE,1f);

        defaultTransformation = (name,val,user) -> {
            if(name!=null && name.length()>0) {
                if(name.endsWith("Date")) {
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
        transformationsForAttr.put(Constants.FILING_COUNTRY,(name,str,user)->QueryBuilders.termQuery(name,str.toUpperCase()));
        transformationsForAttr.put(Constants.DOC_TYPE,(name,str,user)->{
            String ret;
            if(str.toLowerCase().equals("g")) ret = "patents";
            else if(str.toLowerCase().equals("a")) ret = "applications";
            else ret = str;
            return QueryBuilders.termQuery(name,ret);
        });
        transformationsForAttr.put(Constants.DOC_KIND,(name,str,user) ->{
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
        transformationsForAttr.put(Constants.EXPIRATION_DATE,(name,val,user)->{
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
        transformationsForAttr.put(Constants.PRIORITY_DATE,(name,val,user)->{
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
                String attr = Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(field, field.length() > 2 && field.endsWith("_F") ? Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(field.substring(0, field.length() - 2)) : null);
                if (attr != null) {
                    // check for nested)
                    QueryBuilder queryBuilder = QueryBuilders.existsQuery(attr);

                    String root = attr.contains(".") ? attr.substring(0, attr.indexOf(".")) : attr;
                    if (Constants.NESTED_ATTRIBUTES.contains(root)) {
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
        transformationsForAttr.put(Constants.GRANTED,(name,val,user)->{
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
        transformationsForAttr.put("ICLM",(name,val,user)->{
            String attrName = Constants.CLAIMS+"."+Constants.CLAIM;
            QueryBuilder query = QueryBuilders.boolQuery()
                    .mustNot(QueryBuilders.existsQuery(Constants.CLAIMS+"."+Constants.PARENT_CLAIM_NUM))
                    .must(QueryBuilders.queryStringQuery(val).field(attrName).analyzeWildcard(true).defaultOperator(Operator.AND));
            return QueryBuilders.nestedQuery(Constants.CLAIMS,query,ScoreMode.Max);
        });
        transformationsForAttr.put("DCLM",(name,val,user)->{
            String attrName = Constants.CLAIMS+"."+Constants.CLAIM;
            QueryBuilder query = QueryBuilders.boolQuery()
                    .must(QueryBuilders.existsQuery(Constants.CLAIMS+"."+Constants.PARENT_CLAIM_NUM))
                    .must( QueryBuilders.queryStringQuery(val).field(attrName).analyzeWildcard(true).defaultOperator(Operator.AND));
            return QueryBuilders.nestedQuery(Constants.CLAIMS,query,ScoreMode.Max);
        });
        transformationsForAttr.put("TAC",(name,val,user)->{
            return QueryBuilders.boolQuery()
                    .should(QueryBuilders.nestedQuery(Constants.CLAIMS, QueryBuilders.queryStringQuery(val).field(Constants.CLAIMS+"."+Constants.CLAIM).analyzeWildcard(true).defaultOperator(Operator.AND), ScoreMode.Max))
                    .should(QueryBuilders.queryStringQuery(val).field(Constants.ABSTRACT).field(Constants.INVENTION_TITLE).analyzeWildcard(true).defaultOperator(Operator.AND));
        });
        transformationsForAttr.put(Constants.NESTED_CPC_CODES+"."+Constants.CPC_CODES, (name,val,user)->{
            if(val.endsWith("+")) {
                BoolQueryBuilder builder = QueryBuilders.boolQuery();
                if(val.length()>1) {
                    String a = val.substring(0,1);
                    builder = builder.must(QueryBuilders.termQuery(Constants.CPC_SECTION,a));
                    if(val.length()>3) {
                        String b = val.substring(1,3);
                        builder = builder.must(QueryBuilders.termQuery(Constants.CPC_CLASS,b));
                        if(val.length()>4) {
                            String c = val.substring(3,4);
                            builder = builder.must(QueryBuilders.termQuery(Constants.CPC_SUBCLASS,c));
                            int slashIdx = val.indexOf("/",4);
                            if(slashIdx>4) {
                                String d = val.substring(4,slashIdx).trim();
                                builder = builder.must(QueryBuilders.termQuery(Constants.CPC_MAIN_GROUP,d));
                                if(val.length()>slashIdx+2) {
                                    String e = val.substring(slashIdx+1,val.length()-1).trim();
                                    builder = builder.must(QueryBuilders.termQuery(Constants.CPC_SUBGROUP,e));
                                }
                            } else if (val.length()>5) {
                                String d = val.substring(4,val.length()-1).trim();
                                builder = builder.must(QueryBuilders.termQuery(Constants.CPC_MAIN_GROUP,d));
                            }
                        }
                    }
                }
                return builder;
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
    public Parser(String user) {
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
            String attr = Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(prefix, prefix.endsWith("_F")&&prefix.length()>2 ? Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(prefix.substring(0,prefix.length()-2)):null);
            if(attr!=null && prefix.equals(prefix.toUpperCase())&&queryStr.length()>colIdx+1) {
                System.out.println("Found attr: "+attr);
                fullAttr = attr;
                val = queryStr.substring(colIdx+1);
                // check filing
                if(attr.contains(".")) attr = attr.substring(0,attr.indexOf("."));
                if(Constants.NESTED_ATTRIBUTES.contains(attr)) {
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
            if(val.contains(" ")) {
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
                if(Constants.NESTED_ATTRIBUTES.contains(defaultField)||(defaultField.contains(".")&&Constants.NESTED_ATTRIBUTES.contains(defaultField.substring(0,defaultField.indexOf("."))))) {
                    nestedAttr = defaultField.substring(0,defaultField.indexOf("."));
                }
                return new Pair<>(new SpanTermQueryBuilder(defaultField, query.toString()),nestedAttr);
            } else {
                String[] fields = query.toString().split(":",2);
                String field = fields[0];
                if(fields.length>1) {
                    String attr = Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.getOrDefault(field, field.length() > 2 && field.endsWith("_F") ? Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(field.substring(0, field.length() - 2)) : null);
                    if(attr!=null) {
                        String val = fields[1];
                        // check nested
                        String nestedAttr = null;
                        if(Constants.NESTED_ATTRIBUTES.contains(attr)||(attr.contains(".")&&Constants.NESTED_ATTRIBUTES.contains(attr.substring(0,attr.indexOf("."))))) {
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
                String queryStrEnd = queryStr.toLowerCase();
                if(queryStrEnd.contains(":")&&queryStrEnd.length()>queryStrEnd.indexOf(":")+1) {
                    queryStrEnd = queryStrEnd.substring(queryStrEnd.indexOf(":")+1);
                }
                if((queryStrEnd.startsWith("near") || queryStrEnd.startsWith("adj")) && !queryStrEnd.contains(" ") && !queryStrEnd.contains(":") && preIdx!=null&&postIdx!=null) {
                    isProximityQuery = true;
                    int slop;
                    boolean useOrder = queryStrEnd.toLowerCase().startsWith("adj");
                    try {
                        slop = Integer.valueOf(queryStrEnd.toLowerCase().replace("near","").replace("adj",""));
                    } catch(Exception e) {
                        slop = 1;
                    }
                    // valid near query
                    boolean matchAll = false;
                    boolean matchTAC = false;
                    if(!booleanQuery.clauses().get(preIdx).getQuery().toString().contains(":")) {
                        matchAll = true;
                    }
                    if(booleanQuery.clauses().get(preIdx).getQuery().toString().startsWith("TAC:")) {
                        System.out.println("MATCH TAC!!");
                        matchTAC = true;
                    }

                    if(matchAll||matchTAC) {
                        Collection<String> fields;
                        if(matchAll) {
                            fields = defaultFields.keySet();
                        } else {
                            // TAC
                            fields = Arrays.asList(Constants.CLAIMS+"."+Constants.CLAIM,Constants.INVENTION_TITLE,Constants.ABSTRACT);
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
        Parser parser = new Parser("ehallmark");

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
