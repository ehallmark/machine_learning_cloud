package elasticsearch;

import com.google.gson.Gson;
import graphical_modeling.util.Pair;
import lombok.Getter;
import lombok.NonNull;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.rescore.QueryRescoreMode;
import org.elasticsearch.search.rescore.RescoreBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import seeding.google.elasticsearch.attributes.SimilarityAttribute;
import seeding.google.mongo.ingest.IngestPatents;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.attributes.script_attributes.DefaultValueScriptAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
import user_interface.ui_models.filters.AbstractSimilarityGreaterThanFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataSearcher {
    public static final Set<String> SIMILARITY_ATTRIBUTES = new HashSet<>(Arrays.asList(Constants.SIMILARITY_FAST,Attributes.SIMILARITY));
    public static final HighlightBuilder highlighter = new HighlightBuilder()
            .highlighterType("plain")
            .postTags("</span>")
            .preTags("<span style=\"background-color: yellow;\">")
            .requireFieldMatch(true)
            .highlightFilter(true)
            .field(Attributes.CLAIMS)
            .field(Attributes.ABSTRACT)
            .field(Attributes.INVENTION_TITLE)
            .field(Attributes.ASSIGNMENTS + "." + Attributes.CONVEYANCE_TEXT);

    public static final String ARRAY_SEPARATOR = ">>>><<<<";
    @Getter
    private static TransportClient client = MyClient.get();
    private static final int PAGE_LIMIT = 10000;
    private static final boolean debug = false;
    private static final Lock lock = new ReentrantLock();


    private static AbstractAttribute findAttribute(Collection<AbstractAttribute> attributes, String comparator) {
        if (comparator == null) return null;
        return attributes.stream().filter(attr -> comparator.startsWith(attr.getFullName()))
                .flatMap(attr -> {
                    if (attr instanceof NestedAttribute) {
                        return ((NestedAttribute) attr).getAttributes().stream();
                    } else return Stream.of(attr);
                }).filter(attr -> attr.getFullName().equals(comparator))
                .findAny().orElse(null);
    }


    public static String getOrDefaultComparator(String _comparator) {
        return _comparator == null ? Constants.NO_SORT : _comparator;
    }

    public static ElasticSearchResponse searchPatentsGlobal(Collection<AbstractAttribute> attributes, Collection<AbstractFilter> filters, String _comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap, boolean merge, boolean highlight, boolean filterNestedObjects, List<AggregationBuilder> aggregationBuilders) {
        Pair<SearchRequestBuilder,QueryBuilder> request = searchFor(IngestPatents.INDEX_NAME,IngestPatents.TYPE_NAME,attributes,filters,_comparator,sortOrder,maxLimit,highlight,aggregationBuilders, Attributes.FAMILY_ID, Attributes.PUBLICATION_NUMBER_FULL);
        SearchResponse response = request._1.get();
        Aggregations aggs = response.getAggregations();
        long totalCount = response.getHits().totalHits;
        String comparator = getOrDefaultComparator(_comparator);
        boolean isOverallScore = comparator.equals(Constants.SCORE);
        boolean scroll = maxLimit > PAGE_LIMIT;
        List<Item> items = getItemsFromSearchResponse(response, nestedAttrNameMap, isOverallScore, filterNestedObjects, maxLimit, merge, scroll);
        return new ElasticSearchResponse(request._1, request._2,items,aggs,totalCount);
    }

    public static List<Item> getItemsFromSearchResponse(SearchResponse response, Map<String,NestedAttribute> nestedAttrNameMap, boolean isOverallScore, boolean filterNestedObjects, int maxLimit, boolean merge, boolean scroll) {
        return iterateOverSearchResults(response, hit->hitToItem(Attributes.PUBLICATION_NUMBER_FULL,hit,nestedAttrNameMap, isOverallScore, filterNestedObjects), maxLimit, merge, scroll);
    }

    private static Pair<SearchRequestBuilder,QueryBuilder> searchFor(String index, String type, Collection<AbstractAttribute> attributes, Collection<AbstractFilter> filters, String _comparator, SortOrder sortOrder, int maxLimit, boolean highlight, List<AggregationBuilder> aggregationBuilders, String... defaultAttrs) {
        try {
            String[] attrNames = Stream.of(attributes.stream().map(attr->{
                String name = attr.getFullName();
                if(name.contains(".")) {
                    return name.substring(0,name.indexOf("."))+".*";
                } else {
                    return name;
                }
            }),Stream.of(defaultAttrs)).distinct().flatMap(s->s).toArray(size->new String[size]);
            AtomicReference<BoolQueryBuilder> filterBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> queryBuilder = new AtomicReference<>(QueryBuilders.boolQuery());

            if (attributes.stream().anyMatch(attr->attr.getName().equals(Attributes.SIMILARITY)) ||
                    ( _comparator != null && _comparator.equals(Attributes.SIMILARITY)) ||
                    filters.stream().anyMatch(filter->filter.getAttribute()!=null && filter.getAttribute().getName().equals(Attributes.SIMILARITY))) {
                System.out.println("Using similarity...");
                filterBuilder.set(filterBuilder.get().must(QueryBuilders.existsQuery(Attributes.ENC)));
            }

            // Run elasticsearch
            String comparator = getOrDefaultComparator(_comparator);
            boolean isOverallScore = comparator.equals(Constants.SCORE);

            SortBuilder sortBuilder;
            QueryBuilder resortBuilder = null;
            // only pull ids by setting first parameter to empty list
            if(isOverallScore) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else if(comparator.equals(Constants.RANDOM_SORT)) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else if(comparator.equals(Constants.NO_SORT)) {
                sortBuilder = SortBuilders.fieldSort("_doc").order(SortOrder.ASC);
            } else {
                AbstractAttribute comparatorAttr = findAttribute(attributes,comparator);
                if(comparatorAttr != null && comparatorAttr instanceof AbstractScriptAttribute) {
                    if(comparatorAttr instanceof DefaultValueScriptAttribute) {
                        sortBuilder = SortBuilders.fieldSort(comparator).order(sortOrder).missing(((DefaultValueScriptAttribute) comparatorAttr).getDefaultVal());
                    } else {
                        ScriptSortBuilder.ScriptSortType scriptType = comparatorAttr.getType().equals("text") || comparatorAttr.getType().equals("keyword") ? ScriptSortBuilder.ScriptSortType.STRING : ScriptSortBuilder.ScriptSortType.NUMBER;
                        Script sortScript = ((AbstractScriptAttribute) comparatorAttr).getSortScript();
                        System.out.println("Sort script builder: "+sortScript);
                        if(sortScript!=null) {
                            sortBuilder = SortBuilders.scriptSort(sortScript, scriptType).order(sortOrder);
                            if(comparatorAttr instanceof SimilarityAttribute) {
                                filterBuilder.set(filterBuilder.get()
                                        .filter(QueryBuilders.existsQuery(((SimilarityAttribute) comparatorAttr).getFieldName())));
                            }
                        } else {
                            throw new RuntimeException("Unable to create sort script for attribute "+ SimilarPatentServer.humanAttributeFor(comparator));
                            //System.out.println("WARNING:: DEFAULTING TO NO SORT!");
                            //sortBuilder = SortBuilders.fieldSort("_doc").order(SortOrder.ASC);
                        }
                    }
                } else {
                    sortBuilder = SortBuilders.fieldSort(comparator).order(sortOrder);
                }
            }

            //System.out.println("Sorting: "+sortBuilder.toString());

            //System.out.println("Filtering by score: "+isOverallScore);

            //String[] attrArray = attributes.stream().flatMap(attr->SimilarPatentServer.attributeNameHelper(attr,"").stream()).toArray(size -> new String[size]);
            AtomicReference<SearchRequestBuilder> request = new AtomicReference<>(client.prepareSearch(index)
                    .setTypes(type)
                    .setFetchSource(attrNames,new String[]{})
                    .setFrom(0));

            if(aggregationBuilders!=null) {
                aggregationBuilders.forEach(aggregationBuilder -> {
                    request.set(request.get().addAggregation(aggregationBuilder));
                });
            }

            if(maxLimit > PAGE_LIMIT) {
                request.set(request.get()
                        .setSize(Math.min(PAGE_LIMIT,maxLimit))
                        .setScroll(new TimeValue(120000))
                );
            } else {
                request.set(request.get()
                        .setSize(maxLimit)
                );
            }

            if(!comparator.isEmpty()) {
                request.set(request.get().addSort(sortBuilder));
            }

            if(resortBuilder!=null) {
                System.out.println("Rescoring by: "+resortBuilder.toString());
                request.set(request.get().addRescorer(RescoreBuilder.queryRescorer(resortBuilder).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0f).setRescoreQueryWeight(1f)));
            }

            //System.out.println("Looking for similarity greater than...");
            filters.forEach(filter->{
                if(filter instanceof AbstractSimilarityGreaterThanFilter) {
                    AbstractSimilarityGreaterThanFilter simFilter = (AbstractSimilarityGreaterThanFilter)filter;
                    String attrName = simFilter.getAttribute().getFullName();
                    attributes.forEach(attr->{
                        if(attrName.equals(attr.getFullName())) {
                            //System.out.println("Found and setting similarity attribute for filter...");
                            simFilter.setAttribute(attr);
                        }
                    });
                    if(simFilter.isActive()) {
                        //System.out.println("Sim greater than filter is active...");
                        QueryBuilder scriptFilter = simFilter.getScriptFilter();
                        System.out.println(scriptFilter.toString());
                        filterBuilder.set(filterBuilder.get().must(scriptFilter));
                    }

                }
            });

            // filters
            if(debug)System.out.println("Starting ES filters...");
            for (AbstractFilter filter : filters) {
                if(filter.getParent()==null) {
                    if(debug)System.out.println("  filter: "+filter.getName());
                    AtomicReference<BoolQueryBuilder> currentQuery;
                    AtomicReference<BoolQueryBuilder> currentFilter;

                    currentQuery = queryBuilder;
                    currentFilter = filterBuilder;

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
            //System.out.println("Starting ES attributes...");
            for(AbstractAttribute attribute : attributes) {
                if(debug)System.out.println("  attribute: " + attribute.getName());
                if(attribute instanceof NestedAttribute) {
                    ((NestedAttribute) attribute).getAttributes().forEach(childAttr->{
                        handleAttributesHelper(childAttr, isOverallScore, queryBuilder, request);
                    });
                } else {
                    handleAttributesHelper(attribute, isOverallScore, queryBuilder, request);
                }

            }

           // System.out.println("Combining Query...");
            // Add filter to query
            queryBuilder.set(queryBuilder.get().filter(filterBuilder.get()));

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

            return new Pair<>(request.get(), queryBuilder.get());

        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during keyword search: "+e.getMessage());
        }
    }

    public static List<Item> iterateOverSearchResults(SearchResponse response, Function<SearchHit,Item> hitTransformer, int maxLimit, boolean merge) {
        return iterateOverSearchResults(response,hitTransformer,maxLimit,merge,true);
    }

    public static List<Item> iterateOverSearchResults(SearchResponse response, Function<SearchHit,Item> hitTransformer, int maxLimit, boolean merge, final boolean scroll) {
        //Scroll until no hits are returned
        List<Item> items = Collections.synchronizedList(new ArrayList<>());
        if(scroll) lock.lock();
        try {
            long count = 0;
            do {
                System.out.print("-");
                SearchHit[] searchHits = response.getHits().getHits();
                Item[] newItems = new Item[searchHits.length];

                IntStream.range(0, maxLimit < 0 ? newItems.length : Math.min(newItems.length,(int) (maxLimit-count))).forEach(i -> {
                    Item transformation = hitTransformer.apply(searchHits[i]);
                    if (transformation != null) {
                        newItems[i] = transformation;
                    }
                });
                count += searchHits.length;
                if (merge) {
                    items.addAll(Arrays.asList(newItems));
                }
                if (scroll) {
                    response = client.prepareSearchScroll(response.getScrollId()).setScroll(new TimeValue(120000)).execute().actionGet();
                } else {
                    response = null;
                }
            }
            while (response != null && response.getHits().getHits().length != 0 && (maxLimit < 0 || count < maxLimit)); // Zero hits mark the end of the scroll and the while loop.
            if(scroll) {
                ClearScrollResponse clearScrollResponse = client.prepareClearScroll().addScrollId(response.getScrollId()).get();
                System.out.println("Sucessfully cleared scroll: " + clearScrollResponse.isSucceeded());
            }
            System.out.println();
            return items;
        } finally {
            if(scroll) {
                lock.unlock();
            }
        }
    }

    private static void handleAttributesHelper(@NonNull AbstractAttribute attribute, boolean usingScore, AtomicReference<BoolQueryBuilder> queryBuilder, AtomicReference<SearchRequestBuilder> request) {
        boolean componentOfScore = (usingScore && (SIMILARITY_ATTRIBUTES.contains(attribute.getFullName())));
        if (attribute instanceof AbstractScriptAttribute) {
            AbstractScriptAttribute scriptAttribute = (AbstractScriptAttribute) attribute;
            Script script = scriptAttribute.getSortScript();
            System.out.println("Similarity script: "+script);
            if (script != null) {
                request.set(request.get().addScriptField(scriptAttribute.getFullName(), script));
                System.out.println("Found similarity model: "+scriptAttribute.getFullName());
                // add script to query
                if (componentOfScore) {
                    // try adding custom sort script
                    QueryBuilder sortScript = AbstractScriptAttribute.getSortQuery(scriptAttribute.getSortScript(),FiltersFunctionScoreQuery.ScoreMode.SUM, scriptAttribute.getWeight());
                    if(attribute instanceof SimilarityAttribute) {
                        sortScript = QueryBuilders.boolQuery()
                                .filter(QueryBuilders.existsQuery(((SimilarityAttribute) attribute).getFieldName()))
                                .must(sortScript);
                    }
                    if (sortScript != null) {
                        if (attribute.getParent() != null && !attribute.getParent().isObject()) {
                            queryBuilder.set(queryBuilder.get().must(QueryBuilders.nestedQuery(attribute.getRootName(), sortScript, ScoreMode.Max)));
                        } else {
                            queryBuilder.set(queryBuilder.get().must(sortScript));
                        }
                    }
                    System.out.println("Sort script: "+sortScript);
                }
            }
        } else if (componentOfScore) {
            // add default sort
            QueryBuilder query = QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.fieldValueFactorFunction(attribute.getFullName()).missing(0));
            if(attribute.getParent() != null && !attribute.getParent().isObject()) {
                queryBuilder.set(queryBuilder.get().must(QueryBuilders.nestedQuery(attribute.getRootName(), query, ScoreMode.Max)));
            } else {
                queryBuilder.set(queryBuilder.get().must(query));
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

    private static Item hitToItem(String idField, SearchHit hit, Map<String,NestedAttribute> nestedAttrNameMap, boolean isUsingScore, boolean filterNestedObjects) {
        Item item = new Item((String)hit.getSource().get(idField));

        Set<String> foundInnerHits = new HashSet<>();
        if(filterNestedObjects) {
            filterNestedObjects(nestedAttrNameMap,hit,item,foundInnerHits);
        }


        hit.getSource().forEach((k,v)->{
            if(!foundInnerHits.contains(k)) {
                hitToItemHelper(k, v, item.getDataMap(), nestedAttrNameMap, false);
            }
        });
        handleFields(item, hit, foundInnerHits, false);


        // override highlights if any
        handleHighlightFields(item, hit.getHighlightFields(), foundInnerHits, false);

        if(isUsingScore) {
            item.addData(Constants.SCORE, hit.getScore());
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
                    if ((k.endsWith("Date")||k.equals("date")||k.endsWith("_date")||k.contains("_date_")) && val instanceof Number) {
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
