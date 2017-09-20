package user_interface.ui_models.attributes.computable_attributes;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.models.Model;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyAttribute extends ComputableAttribute<List<String>> {
    private Map<String,List<String>> modelMap;
    private Model model;
    public TechnologyAttribute(Model model) {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.AdvancedKeyword));
        this.model=model;
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
    public List<String> attributesFor(Collection<String> items, int limit) {
        if(modelMap==null) {
            modelMap = KeywordModelRunner.loadModelMap(model);
        }
        if(items.isEmpty())return null;
        List<String> techList = modelMap.get(items.stream().findAny().get());
        if(techList==null) return null;
        return techList.stream().map(tech->titleize(tech)).filter(t->t!=null).map(t->technologyCorrectionsMap.getOrDefault(t,t)).collect(Collectors.toList());
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
