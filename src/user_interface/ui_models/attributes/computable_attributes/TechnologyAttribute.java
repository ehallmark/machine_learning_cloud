package user_interface.ui_models.attributes.computable_attributes;

import models.keyphrase_prediction.PredictKeyphraseForFilings;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyAttribute extends ComputableAttribute<List<String>> {
    private static TechnologyAttribute ATTR;
    private Map<String,List<String>> modelMap;
    private TechnologyAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.AdvancedKeyword, AbstractFilter.FilterType.Regexp));
    }

    @Override
    public AbstractAttribute clone() {
        return new TechnologyAttribute();
    }

    public static synchronized TechnologyAttribute getOrCreate() {
        if(ATTR==null) {
            ATTR = new TechnologyAttribute();
        }
        return ATTR;
    }

    @Override
    public String getName() {
        return Constants.TECHNOLOGY;
    }

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public Collection<String> getAllValues() {
        return null;
       // return SimilarPatentServer.getTechTagger().getClassifications().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public List<String> attributesFor(Collection<String> items, int limit, Boolean isApp) {
        if(modelMap==null) {
            synchronized (this) {
                if(modelMap==null) {
                    modelMap = PredictKeyphraseForFilings.loadOrGetTechnologyMap();
                }
            }
        }
        if(items.isEmpty())return null;
        String asset = items.stream().findAny().get();
        String filing;
        if(isApp==null) {
            filing = new AssetToFilingMap().getApplicationDataMap().getOrDefault(asset,new AssetToFilingMap().getPatentDataMap().get(asset));
        } else {
            if(isApp) {
                filing = new AssetToFilingMap().getApplicationDataMap().get(asset);
            } else {
                filing = new AssetToFilingMap().getPatentDataMap().get(asset);
            }
        }
        List<String> techList = modelMap.getOrDefault(asset, filing==null?null:modelMap.get(filing));
        if(techList==null) return null;
        return techList.stream().map(tech->formatTechnologyString(tech)).filter(t->t!=null).collect(Collectors.toList());
    }

    public static String formatTechnologyString(String in) {
        String titleized = titleize(in);
        if(titleized==null) return null;
        return technologyCorrectionsMap.getOrDefault(titleized,titleized);
    }

    private static String titleize(String str) {
        if(str==null||str.length()==0) return null;
        return String.join(" ", Stream.of(str.split("\\s+")).map(s->capitalize(s)).collect(Collectors.toList()));
    }

    @Override
    public Map<String,Object> getNestedFields() {
        Map<String,Object> fields = new HashMap<>();
        Map<String,String> rawType = new HashMap<>();
        rawType.put("type","keyword");
        fields.put("raw",rawType);
        return fields;
    }

    private static Map<String,String> technologyCorrectionsMap = new HashMap<>();
    static {
        technologyCorrectionsMap.put("Electrical Circuits Surrounding","Electrical Circuits");
    }
}
