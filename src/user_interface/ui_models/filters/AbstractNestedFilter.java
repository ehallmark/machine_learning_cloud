package user_interface.ui_models.filters;

import elasticsearch.DataSearcher;
import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractNestedFilter extends AbstractFilter {
    public static boolean debug = false;
    @Getter
    protected Collection<AbstractFilter> filters;
    @Setter
    protected Collection<AbstractFilter> filterSubset;
    protected boolean setParent;
    public AbstractNestedFilter(@NonNull NestedAttribute nestedAttribute, boolean setParent, AbstractFilter... additionalFilters) {
        super(nestedAttribute,FilterType.Nested);
        Collection<AbstractAttribute> attributes = nestedAttribute.getAttributes();
        this.filters = new ArrayList<>(attributes.stream().flatMap(attr->{
            Collection<AbstractFilter> filters = attr.createFilters();
            return filters.stream();
        }).collect(Collectors.toList()));
        if(additionalFilters!=null&&additionalFilters.length>0) {
            this.filters.addAll(Arrays.asList(additionalFilters));
        }
        this.setParent=setParent;
        if(setParent)this.filters.forEach(filter->filter.setParent(this));
    }

    public AbstractNestedFilter(@NonNull NestedAttribute nestedAttribute) {
        this(nestedAttribute,true);
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractNestedFilter((NestedAttribute)attribute);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        return queryHelper(filterSubset,false, true);
    }

    public QueryBuilder getScorableQuery() {
        return queryHelper(filterSubset,true, false);
    }

    public QueryBuilder getNonScorableQuery() {
        return queryHelper(filterSubset,false, false);
    }


    private QueryBuilder queryHelper(Collection<AbstractFilter> subset, boolean usingScore, boolean useAll) {
        if(subset==null) throw new NullPointerException("Subset is null. Please set subset before calling queryHelper().");
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        BoolQueryBuilder filterQuery = QueryBuilders.boolQuery();
        for(AbstractFilter filter : subset) {
            if(filter.isActive()) {
                if(usingScore && filter.contributesToScore()) {
                    boolQuery = boolQuery.must(filter.getFilterQuery());
                } else if(useAll || (!usingScore && !filter.contributesToScore())) {
                    filterQuery = filterQuery.must(filter.getFilterQuery());
                }
            }
        }
        QueryBuilder query = QueryBuilders.boolQuery()
                .must(boolQuery)
                .filter(filterQuery);
        if(!attribute.isObject()) {
            System.out.print("Is nested");
            query = QueryBuilders.nestedQuery(getFullPrerequisite(), query, ScoreMode.Max)
                    .innerHit(new InnerHitBuilder().setHighlightBuilder(DataSearcher.highlighter));
        }
        if(debug) System.out.println("Query for "+getName()+": "+query.toString());
        return query;
    }

    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("Include Filter not supported by scripts");
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        Collection<String> nestedAttributesToFilter = SimilarPatentServer.extractArray(params, getName());
        filterSubset = new ArrayList<>();
        filters.forEach(filter->{
            if(nestedAttributesToFilter.contains(filter.getName())) {
                filter.extractRelevantInformationFromParams(params);
                filterSubset.add(filter);
            }
        });
    }

    @Override
    public String getId() {
        String clazz = NestedAttribute.CLAZZ;
        return ("multiselect-"+clazz+"-"+getName()).replaceAll("[\\[\\] ]","");
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return getOptionsTag(userRoleFunction, null, null);
    }


    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, Function<String,Tag> additionalTagFunction, Function<String,List<String>> additionalInputIdsFunction) {
        String styleString = "display: none; margin-left: 5%; margin-right: 5%;";
        List<AbstractFilter> availableFilters = filters.stream().filter(filter->filter.getAttribute()==null||(filter.getAttribute().isDisplayable()&&userRoleFunction.apply(filter.getAttribute().getRootName()))).collect(Collectors.toList());
        Map<String,List<String>> filterGroups = new TreeMap<>(availableFilters.stream().collect(Collectors.groupingBy(filter->filter.getFullPrerequisite())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));

        String clazz = NestedAttribute.CLAZZ;
        String id = getId();
        return div().with(
                div().with(
                        SimilarPatentServer.technologySelectWithCustomClass(getName(),id,clazz, filterGroups, null)
                ), div().withClass("nested-form-list").with(
                        availableFilters.stream().map(filter->{
                            String collapseId = "collapse-filters-"+filter.getName().replaceAll("[\\[\\]]","");
                            Tag childTag;
                            List<String> inputIds = new ArrayList<>();
                            if(filter.getInputIds()!=null) {
                                inputIds.addAll(filter.getInputIds());
                            }
                            if(filter instanceof AbstractNestedFilter && filterType.equals(FilterType.Nested)) {
                                childTag = ((AbstractNestedFilter) filter).getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction);
                            } else {
                                childTag = filter.getOptionsTag(userRoleFunction);
                                Tag additionalTag = additionalTagFunction!=null ? additionalTagFunction.apply(filter.getName()) : null;
                                if(additionalTag!=null) {
                                    childTag = div().with(additionalTag,childTag);
                                }
                                if(additionalInputIdsFunction!=null) {
                                    List<String> additional = additionalInputIdsFunction.apply(filter.getName());
                                    if(additional!=null) {
                                        inputIds.addAll(additional);
                                    }
                                }
                            }

                            if(inputIds.isEmpty()) inputIds = null;
                            return div().attr("style", styleString).with(
                                    SimilarPatentServer.createAttributeElement(SimilarPatentServer.humanAttributeFor(filter.getName()),filter.getName(),filter.getOptionGroup(),collapseId,childTag,id, filter.getAttributeId(), inputIds, filter.isNotYetImplemented(), filter.getDescription().render())
                            );
                        }).collect(Collectors.toList())
                )
        );
    }

    public boolean isActive() { return filterSubset.size() > 0 && filterSubset.stream().anyMatch(filter->filter.isActive()); }
}
