package elasticsearch;

import com.google.gson.Gson;
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequestBuilder;
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CreateScripts {
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true,false);
        TransportClient client = MyClient.get();
        Collection<AbstractScriptAttribute> scriptAttributes = SimilarPatentServer.getAllTopLevelAttributes().stream()
                .flatMap(attr->attr instanceof NestedAttribute ? ((NestedAttribute) attr).getAttributes().stream() : Stream.of(attr))
                .filter(attr->(attr instanceof AbstractScriptAttribute))
                .map(attr->(AbstractScriptAttribute)attr).collect(Collectors.toList());


        scriptAttributes.forEach(attr->{
            Script script = attr.getScript(false,false);
            Map<String,Object> json = new HashMap<>();
            Map<String,Object> scriptMap = new HashMap<>();
            scriptMap.put("lang",script.getLang());
            scriptMap.put("_source",script.getIdOrCode());
            json.put("script", scriptMap);
            client.admin().cluster().preparePutStoredScript()
                    .setId(attr.getFullName())
                    .setContent(new BytesArray(new Gson().toJson(json)), XContentType.JSON)
                    .get();
        });
    }
}
