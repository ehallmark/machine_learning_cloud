package user_interface.ui_models.engines;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeAssetFilter;

import java.util.*;
import java.util.function.Function;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class PatentSimilarityEngine extends AbstractSimilarityEngine {
    public PatentSimilarityEngine(String tableName) {
        super(tableName, Attributes.PUBLICATION_NUMBER_FULL, Attributes.ENC, false);
    }

    public static List<String> convertToFullPatentNumbers(List<String> rawAssets) {
        Map<String, List<String>> assetFieldToAssetsMap = AbstractIncludeAssetFilter.buildAssetFieldToAssetsMap(false, rawAssets);
        System.out.println("Found "+rawAssets.size()+" patents...");
        List<String> pubNumsWithCountry = assetFieldToAssetsMap.get(Attributes.PUBLICATION_NUMBER_WITH_COUNTRY);
        List<String> pubNums = assetFieldToAssetsMap.get(Attributes.PUBLICATION_NUMBER);
        List<String> pubNumsFull = assetFieldToAssetsMap.get(Attributes.PUBLICATION_NUMBER_FULL);
        List<String> allPubNumFulls = new ArrayList<>();
        if(pubNumsFull!=null) {
            allPubNumFulls.addAll(pubNumsFull);
        }
        if(pubNums!=null&&pubNums.size()>0) {
            allPubNumFulls.addAll(Database.publicationNumberFullForAssets(pubNums, Attributes.PUBLICATION_NUMBER));
        }
        if(pubNumsWithCountry!=null&&pubNumsWithCountry.size()>0) {
            allPubNumFulls.addAll(Database.publicationNumberFullForAssets(pubNumsWithCountry, Attributes.PUBLICATION_NUMBER_WITH_COUNTRY));
        }
        return allPubNumFulls;
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        List<String> patents = preProcess(extractString(req, getId(), "").toUpperCase(), "\\s+", "[^0-9A-Z]");
        Collections.shuffle(patents, new Random(125));
        patents = patents.subList(0, Math.min(patents.size(),1000));
        List<String> allPubNumFulls = convertToFullPatentNumbers(patents);
        System.out.println("Found "+allPubNumFulls.size()+" full publication numbers...");
        return allPubNumFulls;
    }


    @Override
    public String getName() {
        return Constants.PATENT_SIMILARITY;
    }

    @Override
    public String getId() {
        return PATENTS_TO_SEARCH_FOR_FIELD;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 publication per line (eg. US8321100)").withId(getId()).withName(getId())
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        return new PatentSimilarityEngine(tableName);
    }
}
