package user_interface.ui_models.templates;

import com.google.gson.Gson;
import lombok.Getter;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;

import java.util.*;

/**
 * Created by Evan on 6/24/2017.
 */
public class FormTemplate {
    @Getter
    protected String name;
    @Getter
    protected String html;
    public FormTemplate(Map<String,String> map) {
        Map.Entry<String,String> e = map.entrySet().stream().findFirst().orElse(null);
        if(e==null) throw new RuntimeException("Null entry in user_interface.ui_models.FormTemplate");
        this.name=e.getKey();
        this.html=e.getValue();
    }
}
