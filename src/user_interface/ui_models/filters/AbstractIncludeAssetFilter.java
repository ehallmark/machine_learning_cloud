package user_interface.ui_models.filters;

import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.google.elasticsearch.Attributes;
import seeding.google.elasticsearch.attributes.AssetAttribute;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.dataset_lookup.TermsLookupAttribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractIncludeAssetFilter extends AbstractIncludeFilter {
    private static final String ENFORCE_COUNTRY_SUFFIX = "_enforce_cc";
    private static final String ENFORCE_KIND_CODE_SUFFIX = "_enforce_kc";
    protected String assetField;
    protected boolean enforceCountryCode;
    protected boolean enforceKindCode;
    protected boolean isApplication;
    protected String assetPrefix;
    public AbstractIncludeAssetFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, Collection<String> labels) {
        super(attribute, filterType, fieldType, labels);
        if(!(attribute instanceof AssetAttribute)) throw new RuntimeException("Illegal filter type for asset attribute: "+attribute.getFullName());
        this.isApplication = ((AssetAttribute) attribute).isApplication();
        this.assetPrefix = ((AssetAttribute) attribute).getAssetPrefix();
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractIncludeAssetFilter(attribute,filterType,fieldType, labels==null?null:new ArrayList<>(labels));
    }

    @Override
    public QueryBuilder getFilterQuery() {
        String preReq = assetPrefix+assetField;
        System.out.println("Using prereq: "+preReq);
        if(minimumShouldMatch<=1 && !(attribute instanceof TermsLookupAttribute)) {
            return QueryBuilders.termsQuery(preReq,labels);
        } else {
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().minimumShouldMatch(this.minimumShouldMatch);
            for (String label : labels) {
                if (attribute instanceof TermsLookupAttribute) {
                    throw new RuntimeException("Unable to use 'Include Asset Filter' for attribute: "+getFullPrerequisite()+". Reason: Currently not supported.");
                } else {
                    boolQueryBuilder = boolQueryBuilder.should(QueryBuilders.termQuery(preReq, label));
                }
            }
            return boolQueryBuilder;
        }
    }

    @Override
    public List<String> getInputIds() {
        List<String> list = super.getInputIds();
        list.addAll(getAdditionalIds());
        return list;
    }

    public List<String> getAdditionalIds() {
        return Arrays.asList(enforceCountryId(),enforceKindCodeId());
    }

    public String enforceCountryId() {
        return getId()+ENFORCE_COUNTRY_SUFFIX;
    }


    public String enforceKindCodeId() {
        return getId()+ENFORCE_KIND_CODE_SUFFIX;
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        super.extractRelevantInformationFromParams(req);
        if(labels!=null) {
            enforceCountryCode = SimilarPatentServer.extractBool(req, enforceCountryId());
            if(!isApplication) enforceKindCode = SimilarPatentServer.extractBool(req, enforceKindCodeId());
            labels = labels.stream().map(label->{
                return label.toUpperCase().replaceAll("[^A-Z-0-9]", "");
            }).collect(Collectors.toList());
            if (isApplication) {
                if(enforceCountryCode) {
                    assetField = Attributes.APPLICATION_NUMBER_FORMATTED;
                } else {
                    assetField = Attributes.APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY;
                }
            } else {
                if(enforceCountryCode && enforceKindCode) {
                    assetField = Attributes.PUBLICATION_NUMBER_FULL;
                } else if(enforceCountryCode) {
                    assetField = Attributes.PUBLICATION_NUMBER_WITH_COUNTRY;
                } else if(enforceKindCode) {
                    throw new RuntimeException("Must use 'Enforce Country Code' when using 'Enforce Kind Code'.");
                } else {
                    assetField = Attributes.PUBLICATION_NUMBER;
                }
            }
        }
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        ContainerTag tag = div().with(
                textarea().withId(getId()).withClass("form-control").attr("placeholder","1 per line.").withName(getName())
        );

        // add asset specific stuff
        String enforceCountryId = enforceCountryId();
        String enforceKindCodeId = enforceKindCodeId();
        tag = tag.with(
                label("Enforce Country Code?").with(input().withType("checkbox").withId(enforceCountryId).withName(enforceCountryId))
        );
        if(!isApplication) {
            tag = tag.with(
                    label("Enforce Kind Code?").with(input().withType("checkbox").withId(enforceKindCodeId).withName(enforceKindCodeId))
            );
        }
        // check if include
        String minShouldMatchId = getMinShouldMatchId();
        if(minShouldMatchId!=null) {
            // add minimum should match parameter
            tag = tag.with(label("Minimum Should Match").with(input().attr("min","1").withClass("form-control").attr("style","margin-top: 5px; margin-bottom: 5px; margin-left: 10px; width: 65px; display: inline;").withId(minShouldMatchId).withName(minShouldMatchId).withValue("1").withType("number")));
        }
        return tag;
    }

}
