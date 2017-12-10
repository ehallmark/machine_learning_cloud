package elasticsearch;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.NonNull;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractGreaterThanFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataSearcher {
    public static final HighlightBuilder highlighter = new HighlightBuilder()
            .highlighterType("plain")
            .postTags("</span>")
            .preTags("<span style=\"background-color: yellow;\">")
            .requireFieldMatch(true)
            .highlightFilter(true)
            .field(Constants.CLAIMS+"."+Constants.CLAIM)
            .field(Constants.ABSTRACT)
            .field(Constants.INVENTION_TITLE)
            .field(Constants.ASSIGNMENTS+"."+Constants.CONVEYANCE_TEXT);

    public static final String ARRAY_SEPARATOR = "; ";
    @Getter
    private static TransportClient client = MyClient.get();
    private static final String INDEX_NAME = DataIngester.INDEX_NAME;
    private static final String TYPE_NAME = DataIngester.TYPE_NAME;
    private static final int PAGE_LIMIT = 10000;
    private static final boolean debug = false;

    public static List<Item> searchForAssets(Collection<AbstractAttribute> attributes, Collection<AbstractFilter> filters, String comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap, boolean highlight, boolean filterNestedObjects) {
        return searchForAssets(attributes,filters,comparator,sortOrder,maxLimit,nestedAttrNameMap,item->item,true, highlight,filterNestedObjects);
    }

    public static List<Item> searchForAssets(Collection<AbstractAttribute> attributes, Collection<AbstractFilter> filters, String _comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap, ItemTransformer transformer, boolean merge, boolean highlight, boolean filterNestedObjects) {
        try {
            String[] attrNames = attributes.stream().filter(attr->!Constants.FILING_ATTRIBUTES_SET.contains(attr.getRootName())).map(attr->(attr instanceof NestedAttribute) ? attr.getName()+".*" : attr.getFullName()).toArray(size->new String[size]);
            String[] filingAttrNames = attributes.stream().filter(attr->Constants.FILING_ATTRIBUTES_SET.contains(attr.getRootName())).map(attr->(attr instanceof NestedAttribute) ? attr.getName()+".*" : attr.getFullName()).toArray(size->new String[size]);
            // Run elasticsearch
            String comparator = _comparator == null ? "" : _comparator;
            boolean isOverallScore = comparator.equals(Constants.SIMILARITY);
            SortBuilder sortBuilder;
            // only pull ids by setting first parameter to empty list
            if(isOverallScore) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else if (Constants.FILING_ATTRIBUTES_SET.contains(comparator)||(comparator.contains(".")&&Constants.FILING_ATTRIBUTES_SET.contains(comparator.substring(0,comparator.indexOf("."))))) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else if(comparator.equals(Constants.RANDOM_SORT)) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else if(comparator.equals(Constants.NO_SORT)) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else {
                sortBuilder = SortBuilders.fieldSort(comparator).order(sortOrder);
            }

            System.out.println("Filtering by score: "+isOverallScore);
            float similarityThreshold = 0f;
            for(AbstractFilter filter : filters) {
                if((filter instanceof AbstractGreaterThanFilter) && filter.getPrerequisite().equals(Constants.SIMILARITY)) {
                    Number threshold = (Number)((AbstractGreaterThanFilter)filter).getLimit();
                    if(threshold!=null) {
                        similarityThreshold = threshold.floatValue();
                        System.out.println("Setting custom minimum score: " + similarityThreshold);
                        break;
                    }
                }
            }

            //String[] attrArray = attributes.stream().flatMap(attr->SimilarPatentServer.attributeNameHelper(attr,"").stream()).toArray(size -> new String[size]);
            AtomicReference<SearchRequestBuilder> request = new AtomicReference<>(client.prepareSearch(INDEX_NAME)
                    .setScroll(new TimeValue(120000))
                    .setTypes(TYPE_NAME)
                    .setFetchSource(attrNames,new String[]{})
                    .setSize(Math.min(PAGE_LIMIT,maxLimit))
                    .setMinScore(isOverallScore&&similarityThreshold>0f?similarityThreshold:0f)
                    .setFrom(0));

            if(!comparator.isEmpty() && sortBuilder!=null) {
                request.set(request.get().addSort(sortBuilder));
            }
            AtomicReference<BoolQueryBuilder> filterBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> queryBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> parentFilterBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> parentQueryBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            // filters
            if(debug)System.out.println("Starting ES filters...");
            for (AbstractFilter filter : filters) {
                if(filter.getParent()==null) {
                    if(debug)System.out.println("  filter: "+filter.getName());
                    AtomicReference<BoolQueryBuilder> currentQuery;
                    AtomicReference<BoolQueryBuilder> currentFilter;
                    if(Constants.FILING_ATTRIBUTES_SET.contains(filter.getAttribute().getRootName())) {
                        currentQuery = parentQueryBuilder;
                        currentFilter = parentFilterBuilder;
                    } else {
                        currentQuery = queryBuilder;
                        currentFilter = filterBuilder;
                    }
                    if (filter.contributesToScore() && isOverallScore) {
                        currentQuery.set(currentQuery.get().must(filter.getFilterQuery().boost(10)));
                    } else if (isOverallScore && filter instanceof AbstractNestedFilter) {
                        AbstractNestedFilter nestedFilter = (AbstractNestedFilter)filter;
                        QueryBuilder scorableQuery = nestedFilter.getScorableQuery();
                        QueryBuilder nonScorableQuery = nestedFilter.getNonScorableQuery();
                        if(scorableQuery!=null) {
                            currentQuery.set(currentQuery.get().must(scorableQuery.boost(10)));
                        }
                        if(nonScorableQuery!=null) {
                            currentFilter.set(currentFilter.get().must(nonScorableQuery.boost(0)));
                        }
                    } else {
                        currentFilter.set(currentFilter.get().must(filter.getFilterQuery().boost(0)));
                    }
                }
            }
            System.out.println("Starting ES attributes...");
            AtomicReference<InnerHitBuilder> innerHitBuilder = new AtomicReference<>(new InnerHitBuilder().setSize(1).setFrom(0).setFetchSourceContext(new FetchSourceContext(true,filingAttrNames,new String[]{})));
            for(AbstractAttribute attribute : attributes) {
                boolean isFilingType = Constants.FILING_ATTRIBUTES_SET.contains(attribute.getRootName());
                AtomicReference<BoolQueryBuilder> queryBuilderToUse = isFilingType ? parentQueryBuilder : queryBuilder;
                if(debug)System.out.println("  attribute: " + attribute.getName());
                if(attribute instanceof NestedAttribute) {
                    ((NestedAttribute) attribute).getAttributes().forEach(childAttr->{
                        handleAttributesHelper(childAttr, comparator, isOverallScore, queryBuilderToUse, request, innerHitBuilder);
                    });
                } else {
                    handleAttributesHelper(attribute, comparator, isOverallScore, queryBuilderToUse, request, innerHitBuilder);
                }

            }

            if(highlight) {
                innerHitBuilder.set(innerHitBuilder.get().setHighlightBuilder(highlighter));
            }

            // special case to handle similarity greater than filter without sorting by similarity
            if(!isOverallScore && similarityThreshold > 0f) {
                throw new RuntimeException("Unable to apply filter: Similarity Greater Than "+similarityThreshold+". Reason: Must sort by Similarity.");
            } // end of special case

            System.out.println("Combining Query...");
            // Add filter to query
            queryBuilder.set(queryBuilder.get().filter(filterBuilder.get()));
            parentQueryBuilder.set(parentQueryBuilder.get().filter(parentFilterBuilder.get()));
            queryBuilder.set(queryBuilder.get().must(
                    new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME,parentQueryBuilder.get(),true)
                            .innerHit(innerHitBuilder.get())
            ));

            if(comparator.equals(Constants.RANDOM_SORT)) {
                queryBuilder.set(queryBuilder.get().must(QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.randomFunction(System.currentTimeMillis()))));
            }

           if(highlight) {
               // possible highlighting
               request.set(request.get().highlighter(highlighter));
            }

            // Set query
            if(debug) {
               String searchReqStr = request.get().toString();
               System.out.println(searchReqStr);
            }

            request.set(request.get().setQuery(queryBuilder.get()));

            SearchResponse response = request.get().get();
            return iterateOverSearchResults(response, hit->transformer.transform(hitToItem(hit,nestedAttrNameMap, isOverallScore, filterNestedObjects)), maxLimit, merge);

        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during keyword search: "+e.getMessage());
        }
    }

    public static List<Item> iterateOverSearchResults(SearchResponse response, Function<SearchHit,Item> hitTransformer, int maxLimit, boolean merge) {
        //Scroll until no hits are returned
        List<Item> items = Collections.synchronizedList(new ArrayList<>());
        long count = 0;
        do {
            System.out.print("-");
            SearchHit[] searchHits = response.getHits().getHits();
            Item[] newItems = new Item[searchHits.length];
            IntStream.range(0,newItems.length).parallel().forEach(i->{
                Item transformation = hitTransformer.apply(searchHits[i]);
                if(transformation!=null) {
                    newItems[i]=transformation;
                }
            });
            count+=searchHits.length;
            if(merge) {
                items.addAll(Arrays.asList(newItems));
            }
            response = client.prepareSearchScroll(response.getScrollId()).setScroll(new TimeValue(120000)).execute().actionGet();
        } while(response.getHits().getHits().length != 0 && (maxLimit < 0 || count < maxLimit)); // Zero hits mark the end of the scroll and the while loop.
        ClearScrollResponse clearScrollResponse = client.prepareClearScroll().addScrollId(response.getScrollId()).get();
        System.out.println();
        System.out.println("Sucessfully cleared scroll: "+clearScrollResponse.isSucceeded());
        return items;
    }

    private static void handleAttributesHelper(@NonNull AbstractAttribute attribute, @NonNull String comparator, boolean usingScore, AtomicReference<BoolQueryBuilder> queryBuilder, AtomicReference<SearchRequestBuilder> request, AtomicReference<InnerHitBuilder> innerHitBuilder) {
        boolean isParentAttr = Constants.FILING_ATTRIBUTES_SET.contains(attribute.getRootName());
        boolean componentOfScore = (usingScore && Constants.OVERALL_SCORE_ATTRIBUTES.contains(attribute.getFullName()))
                || (attribute.getFullName().equals(comparator) && isParentAttr);
        if (attribute instanceof AbstractScriptAttribute) {
            System.out.println("  Script Component... " + attribute.getFullName());
            AbstractScriptAttribute scriptAttribute = (AbstractScriptAttribute) attribute;
            Script script = scriptAttribute.getScript();
            if (script != null) {
                if (isParentAttr) {
                    innerHitBuilder.set(innerHitBuilder.get().addScriptField(scriptAttribute.getName(), script));
                    System.out.println("Adding script to inner hit builder: " + script.getIdOrCode());

                } else {
                    request.set(request.get().addScriptField(scriptAttribute.getFullName(), script));
                    System.out.println("Adding script to main request: " + script.getIdOrCode());
                }
                // add script to query
                if (componentOfScore) {
                    System.out.println("Component of script score...");
                    // try adding custom sort script
                    QueryBuilder sortScript = scriptAttribute.getSortQuery();
                    if (sortScript != null) {
                        if (attribute.getParent() != null && !attribute.getParent().isObject()) {
                            System.out.println("Is nested");
                            queryBuilder.set(queryBuilder.get().must(QueryBuilders.nestedQuery(attribute.getRootName(), sortScript, ScoreMode.Avg)));
                        } else {
                            System.out.println("Not nested.");
                            queryBuilder.set(queryBuilder.get().must(sortScript));
                        }
                    }
                    System.out.println("Is Script and Component of score: "+sortScript);
                }
            }
        } else if (componentOfScore) {
            System.out.println("  Score Component... " + attribute.getFullName());
            // add default sort
            if (isParentAttr) {
                String sortScript = "doc['" + attribute.getFullName() + "'].empty ? 0 : ("+(usingScore ? "_score *":"")+" doc['" + attribute.getFullName() + "'].value)";
                System.out.println("Using custom score component on filings: " + sortScript);
                QueryBuilder query = QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.scriptFunction(new Script(ScriptType.INLINE, "expression", sortScript, Collections.emptyMap())));
                if(attribute.getParent() != null && !attribute.getParent().isObject()) {
                    queryBuilder.set(queryBuilder.get().must(QueryBuilders.nestedQuery(attribute.getRootName(), query, ScoreMode.Avg)));
                } else {
                    queryBuilder.set(queryBuilder.get().must(query));
                }
            } else {
                QueryBuilder query = QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.fieldValueFactorFunction(attribute.getFullName()).missing(0));
                if(attribute.getParent() != null && !attribute.getParent().isObject()) {
                    queryBuilder.set(queryBuilder.get().must(QueryBuilders.nestedQuery(attribute.getRootName(), query, ScoreMode.Avg)));
                } else {
                    queryBuilder.set(queryBuilder.get().must(query));
                }
            }
        }
    }

    private static void filterNestedObjects(Map<String,NestedAttribute> nestedAttrNameMap, SearchHit hit, Item item, Set<String> foundInnerHits) {
        // try to get inner hits from nested attrs
        if(hit.getInnerHits()==null) return;
        nestedAttrNameMap.entrySet().forEach(e -> {
            SearchHits nestedHits = hit.getInnerHits().get(e.getKey());
            if (nestedHits != null) {
                SearchHit[] innerHits = nestedHits.getHits();
                if (innerHits != null && innerHits.length > 0) {
                    for (SearchHit nestedHit : innerHits) {
                        nestedHit.getSource().forEach((k, v) -> {
                            hitToItemHelper(e.getKey() + "." + k, v, item.getDataMap(), nestedAttrNameMap, true);
                        });
                        handleFields(item, nestedHit, Collections.emptySet(), true);
                        // handle highlight
                        // test
                        if (debug) {
                            System.out.println(" Nested inner fields: " + new Gson().toJson(nestedHit.getFields()));
                            System.out.println(" Nested inner source: " + new Gson().toJson(nestedHit.getSource()));
                            System.out.println(" Nested inner highlighting: " + new Gson().toJson(nestedHit.getHighlightFields()));
                        }
                        handleHighlightFields(item, nestedHit.getHighlightFields(), Collections.emptySet(), true);
                    }
                }
                foundInnerHits.add(e.getKey());
                e.getValue().getAttributes().forEach(attr -> foundInnerHits.add(attr.getFullName()));
            }
        });
    }

    private static Item hitToItem(SearchHit hit, Map<String,NestedAttribute> nestedAttrNameMap, boolean isUsingScore, boolean filterNestedObjects) {
        Item item = new Item(hit.getId());

        Set<String> foundInnerHits = new HashSet<>();
        if(filterNestedObjects) {
            filterNestedObjects(nestedAttrNameMap,hit,item,foundInnerHits);
        }

        //if(debug) {
        //    System.out.println("fields: "+new Gson().toJson(hit.getFields()));
        //    System.out.println("source: "+new Gson().toJson(hit.getSource()));
        //    System.out.println("highlights: "+new Gson().toJson(hit.getHighlightFields()));
        // }

        hit.getSource().forEach((k,v)->{
            if(!foundInnerHits.contains(k)) {
                hitToItemHelper(k, v, item.getDataMap(), nestedAttrNameMap, false);
            }
        });
        handleFields(item, hit, foundInnerHits, false);


        SearchHits innerHit = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
        if(innerHit != null) {
            SearchHit[] innerHits = innerHit.getHits();
            if(innerHits != null && innerHits.length > 0) {
                SearchHit firstHit = innerHits[0];
                // try nested inner hits
                if(filterNestedObjects) {
                    filterNestedObjects(nestedAttrNameMap,firstHit,item,foundInnerHits);
                }

                // get regular attrs
                firstHit.getSource().forEach((k,v)->{
                    if(!foundInnerHits.contains(k)) {
                        hitToItemHelper(k, v, item.getDataMap(), nestedAttrNameMap, false);
                    }
                });
                handleFields(item, firstHit, foundInnerHits, false);
                handleHighlightFields(item, firstHit.getHighlightFields(), foundInnerHits, false);
                // if(debug) {
               //     System.out.println(" Filings inner fields: " + new Gson().toJson(firstHit.getFields()));
               //     System.out.println(" Filings inner source: " + new Gson().toJson(firstHit.getSource()));
               //     System.out.println(" Filings inner highlighting: " + new Gson().toJson(firstHit.getHighlightFields()));
               // }
            }
        }

        // override highlights if any
        handleHighlightFields(item, hit.getHighlightFields(), foundInnerHits, false);

        if(isUsingScore) {
            item.addData(Constants.SIMILARITY, hit.getScore());
        }
        return item;
    }


    private static void handleHighlightFields(Item item, Map<String,HighlightField> highlightFieldMap, Set<String> alreadyFound, boolean append) {
        if(highlightFieldMap != null) {
            highlightFieldMap.entrySet().forEach(e->{
                if(!alreadyFound.contains(e.getKey())) {
                    Text[] fragments = e.getValue().getFragments();
                    if (fragments != null) {
                        StringJoiner sj = new StringJoiner("<br />", "", "");
                        for (Text fragment : fragments) {
                            sj.add(fragment.toString());
                        }
                        if(append && item.getDataMap().containsKey(e.getKey() + Constants.HIGHLIGHTED)) {
                            item.addData(e.getKey() + Constants.HIGHLIGHTED, String.join(ARRAY_SEPARATOR, item.getData(e.getKey() + Constants.HIGHLIGHTED).toString(), sj.toString()));
                        } else {
                            item.addData( e.getKey() + Constants.HIGHLIGHTED, sj.toString());
                        }
                    }
                }
            });
        }
    }


    private static void handleFields(Item item, SearchHit hit, Set<String> alreadyFound, boolean append) {
        Map<String,SearchHitField> searchHitFieldMap = hit.getFields();
        if(searchHitFieldMap==null) return;

        searchHitFieldMap.forEach((k,v)->{
            Object val = v.getValue();
            if(!alreadyFound.contains(k)) {
                if (val != null) {
                    // check for date field
                    if (k.endsWith("Date") && val instanceof Number) {
                        long longValue = ((Number) val).longValue();
                        val = Instant.ofEpochMilli(longValue).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_DATE);
                    }
                    if(append && item.getDataMap().containsKey(k)) {
                        item.addData(k, String.join(ARRAY_SEPARATOR, item.getData(k).toString(), val.toString()));
                    } else {
                        item.addData(k, val);
                    }
                }
            }
        });
    }

    private static Map<String,Object> mapAccumulator(List<Map<String,Object>> maps) {
        Map<String,Object> newMap = new HashMap<>();
        Set<String> keys = maps.stream().flatMap(m->m.keySet().stream()).collect(Collectors.toSet());
        keys.forEach(key->{
            newMap.put(key, String.join(ARRAY_SEPARATOR, maps.stream().map(m->m.getOrDefault(key," ").toString()).collect(Collectors.toList())).trim());
        });
        return newMap;
    }

    // set append=true to append singleton values instead of replacing them
    private static void hitToItemHelper(String attrName, Object v, Map<String,Object> itemDataMap, Map<String,NestedAttribute> nestedAttrNameMap, boolean append) {
        if (v == null) return;
        if (v instanceof Map) {
            // get attr
            NestedAttribute attr = nestedAttrNameMap.get(attrName);
            if (attr != null) {
                for (AbstractAttribute nestedAttr : attr.getAttributes()) {
                    Object v2 = ((Map) v).get(nestedAttr.getName());
                    if (v2 != null) {
                        hitToItemHelper(attrName + "." + nestedAttr.getName(), v2, itemDataMap, nestedAttrNameMap, append);
                    }
                }
            }

        } else if (v instanceof List || v instanceof Object[]) {
            if (v instanceof Object[]) v = Arrays.stream((Object[]) v).collect(Collectors.toList());
            if (nestedAttrNameMap.containsKey(attrName)) {
                // add nested object data
                List<Map<String,Object>> nestedDataMaps = new ArrayList<>();
                for (Object obj : (List) v) {
                    if (obj instanceof Map) {
                        Map<String,Object> nestedDataMap = new HashMap<>();
                        hitToItemHelper(attrName, obj, nestedDataMap, nestedAttrNameMap, append);
                        nestedDataMaps.add(nestedDataMap);
                    }
                }
                if(nestedDataMaps.size()>0) {
                    Map<String,Object> reduction = mapAccumulator(nestedDataMaps);
                    if(reduction!=null && reduction.size()>0) itemDataMap.putAll(reduction);
                }
            } else {
                // add as normal list
                if (v != null) {
                    hitToItemHelper(attrName, String.join(ARRAY_SEPARATOR, (List<String>) ((List) v).stream().map(v2 -> v2.toString()).collect(Collectors.toList())), itemDataMap, nestedAttrNameMap, append);
                }
            }
        } else {
            if(append && itemDataMap.containsKey(attrName)) {
                itemDataMap.put(attrName, String.join(ARRAY_SEPARATOR, itemDataMap.get(attrName).toString(), v.toString()));
            } else {
                itemDataMap.put(attrName, v);
            }
        }
    }
}
