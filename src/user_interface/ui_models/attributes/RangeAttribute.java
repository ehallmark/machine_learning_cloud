package user_interface.ui_models.attributes;

/**
 * Created by ehallmark on 12/7/17.
 */
public interface RangeAttribute {
    Number min();
    Number max();
    int nBins();
    String valueSuffix();
    String getFullName();
}
