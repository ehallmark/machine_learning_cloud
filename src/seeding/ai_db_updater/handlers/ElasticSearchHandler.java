package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import seeding.Database;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**

 */
public class ElasticSearchHandler extends SAXFullTextHandler {
    public ElasticSearchHandler(PortfolioList.Type type) {
        super(type, false);
    }

    @Override
    protected void update() {
        if (pubDocNumber != null && !fullDocuments.isEmpty() && !shouldTerminate) {
            try {
                DataIngester.ingestAsset(pubDocNumber, type, String.join(" ", fullDocuments));
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }
    }

    @Override
    public CustomHandler newInstance() {
        return new ElasticSearchHandler(type);
    }
}
