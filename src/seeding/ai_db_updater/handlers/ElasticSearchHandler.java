package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import seeding.Database;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**

 */
public class ElasticSearchHandler extends SAXFullTextHandler {
    private final Map<String,Map<String,Object>> dataMap;
    private ElasticSearchHandler(PortfolioList.Type type, Map<String,Map<String,Object>> dataMap) {
        super(type, false);
        this.dataMap=dataMap;
    }

    public ElasticSearchHandler(PortfolioList.Type type) {
        this(type, Collections.synchronizedMap(new HashMap<>()));
    }

    @Override
    protected void update() {
        if (pubDocNumber != null && !fullDocuments.isEmpty() && !shouldTerminate) {
            synchronized (dataMap) {
                Map<String,Object> itemData = new HashMap<>(3);
                itemData.put("pub_doc_number",pubDocNumber);
                itemData.put("doc_type",type.toString());
                itemData.put("tokens", String.join(" ", fullDocuments));
                dataMap.put(pubDocNumber, itemData);
                if (dataMap.size() > 5000) {
                    DataIngester.ingestAssets(dataMap,true);
                }
            }
        }
    }

    @Override
    public CustomHandler newInstance() {
        return new ElasticSearchHandler(type,dataMap);
    }

    @Override
    public void save() {
        if(dataMap.size() > 0) {
            DataIngester.ingestAssets(dataMap,true);
        }
    }
}
