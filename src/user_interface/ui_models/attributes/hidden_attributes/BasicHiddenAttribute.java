package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public abstract class BasicHiddenAttribute<T> extends HiddenAttribute<T> {
    protected String preReq;
    public BasicHiddenAttribute(String preReq) {
        this.preReq=preReq;
    }
    @Override
    public T handleIncomingData(String name, Map<String,Object> allData, Map<String, T> myData, boolean isApp) {
        Object obj = allData.get(preReq);
        if(obj==null) return null;
        return (T) obj;
    }

}
