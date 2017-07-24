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
    private final Map<String,String> dataMap;
    private ElasticSearchHandler(PortfolioList.Type type, Map<String,String> dataMap) {
        super(type, false);
        this.dataMap=dataMap;
    }

    public ElasticSearchHandler(PortfolioList.Type type) {
        this(type, Collections.synchronizedMap(new HashMap<>()));
    }

    @Override
    protected void update() {
        if (pubDocNumber != null && !fullDocuments.isEmpty() && !shouldTerminate) {
            dataMap.put(pubDocNumber, String.join(" ", fullDocuments));
            if(dataMap.size()> 5000) {
                DataIngester.ingestAssets(dataMap, type);
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
            DataIngester.ingestAssets(dataMap,type);
        }
    }
}
