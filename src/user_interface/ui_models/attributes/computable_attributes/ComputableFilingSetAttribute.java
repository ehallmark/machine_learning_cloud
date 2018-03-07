package user_interface.ui_models.attributes.computable_attributes;

import lombok.Setter;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 6/15/17.
 */
public abstract class ComputableFilingSetAttribute extends ComputableFilingAttribute<Boolean> {

    @Setter
    private Set<String> filings;
    public ComputableFilingSetAttribute(File file) {
        super(file,Arrays.asList(AbstractFilter.FilterType.BoolTrue, AbstractFilter.FilterType.BoolFalse));
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

    public Set<String> getFilings() {
        if(filings==null) {
            synchronized (ComputableFilingSetAttribute.class) {
                if (filings==null) {
                    filings=(Set<String>) Database.tryLoadObject(file);
                    if(filings==null) {
                        filings = Collections.synchronizedSet(new HashSet<>());
                    }
                }
            }
        }
        return filings;
    }

    @Override
    public Boolean handleFiling(String filing) {
        if(filings==null) {
            getFilings();
        }
        return filings.contains(filing);
    }

    @Override
    public void save() {
        if(filings!=null&&filings.size()>0) {
            synchronized (ComputableFilingSetAttribute.class) {
                Database.trySaveObject(filings,file);
            }
        }
    }

    @Override
    public void saveMap(Map<String,Boolean> map) {
        throw new UnsupportedOperationException("saveMap");
    }

    @Override
    public  Map<String,Boolean> loadMap() {
        throw new UnsupportedOperationException("loadMap");
    }
}
