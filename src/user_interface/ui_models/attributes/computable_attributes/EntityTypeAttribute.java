package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.*;

/**
 * Created by Evan on 6/17/2017.
 */
public class EntityTypeAttribute extends ComputableFilingAttribute<String> {
    public static final File filingsByEntityTypeMapFile = new File(Constants.DATA_FOLDER+"filings_by_entity_type_map.jobj");

    private Map<String,Set<String>> typeToFilingMap;
    private Set<String> micro;
    private Set<String> small;
    private Set<String> large;
    public EntityTypeAttribute() {
        super(filingsByEntityTypeMapFile,Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE_ENTITY_TYPE;
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public Collection<String> getAllValues() {
        return Arrays.asList(Constants.SMALL,Constants.MICRO,Constants.LARGE);
    }

    @Override
    public String handleFiling(String filing) {

        if(typeToFilingMap==null) {
            typeToFilingMap = getTypeToFilingMap();
        }

        if(micro.contains(filing)) return Constants.MICRO;
        else if(small.contains(filing)) return Constants.SMALL;
        else if(large.contains(filing)) return Constants.LARGE;
        return null;
    }

    public Map<String,Set<String>> getTypeToFilingMap() {
        if(typeToFilingMap==null) {
            synchronized (EntityTypeAttribute.class) {
                if(typeToFilingMap==null) {
                    typeToFilingMap=(Map<String,Set<String>>)Database.tryLoadObject(file);
                    if(typeToFilingMap==null) {
                        typeToFilingMap = Collections.synchronizedMap(new HashMap<>());
                        getAllValues().forEach(val->{
                            typeToFilingMap.put(val,Collections.synchronizedSet(new HashSet<>()));
                        });
                    }
                }
            }
        }
        if(typeToFilingMap!=null) {
            large = typeToFilingMap.get(Constants.LARGE);
            small = typeToFilingMap.get(Constants.SMALL);
            micro = typeToFilingMap.get(Constants.MICRO);
        }
        return typeToFilingMap;
    }


    @Override
    public void save() {
        if(typeToFilingMap!=null&&typeToFilingMap.size()>0) {
            synchronized (EntityTypeAttribute.class) {
                Database.trySaveObject(typeToFilingMap,file);
            }
        }
    }

    @Override
    public void saveMap(Map<String,String> map) {
        throw new UnsupportedOperationException("saveMap");
    }

    @Override
    public Map<String,String> loadMap() {
        throw new UnsupportedOperationException("loadMap");
    }
}
