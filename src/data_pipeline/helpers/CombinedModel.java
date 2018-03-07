package data_pipeline.helpers;

import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.nn.api.Model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedModel<T extends Model> implements Serializable {
    private static final long serialVersionUID = 1L;
    @Getter @Setter
    protected transient Map<String,T> nameToNetworkMap;
    @Getter
    private Set<String> networkNames;
    @Getter
    private Class<T> clazz;
    public CombinedModel(Map<String,T> nameToNetworkMap, Class<T> clazz) {
        this.nameToNetworkMap=nameToNetworkMap;
        this.networkNames = Collections.synchronizedSet(new HashSet<>(nameToNetworkMap.keySet()));
        this.clazz = clazz;
    }
}
