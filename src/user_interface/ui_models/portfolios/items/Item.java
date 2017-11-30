package user_interface.ui_models.portfolios.items;

import lombok.Getter;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public class Item  {
    @Getter
    protected Map<String,Object> dataMap = new HashMap<>();
    protected String name;

    public Item(String name) {
        setName(name);
    }

    public Item clone() {
        Item item = new Item(getName());
        item.dataMap=new HashMap<>(dataMap);
        return item;
    }

    public void setName(String name) {
        this.name=name;
        addData(Constants.NAME,name);
    }

    public String getName() {
        return name;
    }

    public Map<String,String> getDataAsMap(List<String> attributes,boolean useHighlighter) {
        return attributes.stream().map(attr->{
            Object cell = useHighlighter ? dataMap.getOrDefault(attr+Constants.HIGHLIGHTED, dataMap.get(attr)) : dataMap.get(attr);
            return new Pair<>(attr,cell==null? "": ((cell instanceof Double || cell instanceof Float) ? (((Number)cell).doubleValue()==(double) ((Number)cell).intValue() ? String.valueOf(((Number)cell).intValue()) : String.format("%.1f",cell)) : cell.toString()));
        }).collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));
    }

    public Object getData(String param) {
        return dataMap.get(param);
    }

    public void addData(String param, Object data) {
        dataMap.put(param,data);
    }

    @Override
    public boolean equals(Object other) {
        return name.equals(other.toString());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
