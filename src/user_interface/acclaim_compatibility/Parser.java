package user_interface.acclaim_compatibility;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.deeplearning4j.berkeley.Pair;
import org.elasticsearch.index.query.RangeQueryBuilder;
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

    private static Pair<String,Boolean> replaceAcclaimName(String queryStr, Query query) {
        boolean isFiling = false;
        int colIdx = queryStr.indexOf(":");
        if(colIdx>0) {
            String prefix = queryStr.substring(0,colIdx);
            if(prefix.equals(prefix.toUpperCase()) && Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.containsKey(prefix)) {
                String attr = Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP.get(prefix);
                queryStr = attr+queryStr.substring(colIdx);
                // check filing
                if(attr.contains(".")) attr = attr.substring(0,attr.indexOf("."));
                if(Constants.FILING_ATTRIBUTES_SET.contains(attr)) {
                    isFiling = true;
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
        //if(queryStr.equals("near")||queryStr.equals("+near")) {
        //    System.out.println("Foound near!!!!!");
        //}
        return new Pair<>(queryStr,isFiling);
    }

    public Pair<String,String> parseAcclaimQuery(String text) {
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
        String filingQuery = "";
        String mainQuery = "";

        BooleanQuery booleanQuery;
        if(query instanceof BooleanQuery) {
            booleanQuery = (BooleanQuery)query;
            return parseAcclaimQueryHelper(booleanQuery);
        } else {
            Pair<String,Boolean> p = replaceAcclaimName(query.toString(),query);
            boolean isFiling = p.getSecond();
            if(isFiling) {
                filingQuery = p.getFirst();
            } else {
                mainQuery = p.getFirst();
            }
        }
        return new Pair<>(filingQuery,mainQuery);
    }

    public Pair<String,String> parseAcclaimQueryHelper(BooleanQuery booleanQuery) {
        StringBuilder mainBuilder = new StringBuilder();
        StringBuilder filingBuilder = new StringBuilder();

        for(int i = 0; i < booleanQuery.clauses().size(); i++) {
            boolean hadFiling = false;
            boolean hadMain = false;
            BooleanClause c = booleanQuery.clauses().get(i);
            Query subQuery = c.getQuery();
            if(subQuery instanceof BooleanQuery) {
                Pair<String,String> p = parseAcclaimQueryHelper((BooleanQuery)subQuery);
                String filingQuery = p.getFirst();
                String mainQuery = p.getSecond();
                if(filingQuery.length()>0) {
                    filingBuilder.append(c.getOccur().toString());
                    hadFiling = true;
                    filingBuilder.append("(");
                    filingBuilder.append(filingQuery);
                    filingBuilder.append(")");
                }
                if(mainQuery.length()>0) {
                    mainBuilder.append(c.getOccur().toString());
                    hadMain = true;
                    mainBuilder.append("(");
                    mainBuilder.append(mainQuery);
                    mainBuilder.append(")");
                }
            } else {
                String queryStr = subQuery.toString();
                Pair<String,Boolean> p = replaceAcclaimName(queryStr,subQuery);
                if(p.getSecond()) {
                    hadFiling=true;
                    filingBuilder.append(c.getOccur().toString());
                    filingBuilder.append(p.getFirst());
                } else {
                    hadMain=true;
                    mainBuilder.append(c.getOccur().toString());
                    mainBuilder.append(p.getFirst());
                }
            }

            if(i != booleanQuery.clauses().size() - 1) {
                if(hadFiling) {
                    filingBuilder.append(" ");
                }
                if(hadMain) {
                    mainBuilder.append(" ");
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

        return new Pair<>(filingBuilder.toString(),mainBuilder.toString());
    }



    public static void main(String[] args) throws Exception {
        Parser parser = new Parser();

        Pair<String,String> res = parser.parseAcclaimQuery("(TTL:foo* NEAR foot OR PRIRD:[NOW-2000DAYS TO NOW+2YEARS] (something && ACLM:(else OR elephant && \"phrase of something\"))) OR -field2:bar AND NOT foorbar");


        System.out.println("Filing query: "+res.getFirst());
        System.out.println("Main query: "+res.getSecond());
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
