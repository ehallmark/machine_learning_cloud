package seeding.ai_db_updater.iterators;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.CustomHandler;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.portfolios.PortfolioList;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**

 */
public class DatabaseIterator {
    private static boolean debug = false;
    @Setter
    protected static Map<String,INDArray> lookupTable;
    @Setter
    protected static Collection<ComputableAttribute> computableAttributes;
    private LocalDate startDate;
    private LocalDate endDate;
    private Collection<AbstractAttribute> topLevelAttributes;
    public DatabaseIterator(LocalDate startDate, LocalDate endDate) {
        this.endDate=endDate;
        this.startDate=startDate;
    }

    protected void init() {
        this.topLevelAttributes = SimilarPatentServer.getAllTopLevelAttributes();
    }

    public void save() {
        DataIngester.finishCurrentMongoBatch();
        if (computableAttributes != null) {
            computableAttributes.forEach(attr -> {
                attr.save();
            });
        }
    }

    private void saveElasticSearch(String name, String filingName, Map<String,Object> doc) {
        if(filingName != null) {
            if(debug)System.out.println("filing: "+filingName);
            DataIngester.ingestBulk(name, filingName, doc, true);
        }
    }

    public void run() throws SQLException {
        Database.setupSeedConn();
        this.init();

        String patentDBQuery = "select * from patentdb where pub_date::date >= ? and pub_date::date < ?";
        PreparedStatement patentDBStatement = Database.seedConn.prepareStatement(patentDBQuery);
        patentDBStatement.setDate(1, Date.valueOf(startDate));
        patentDBStatement.setDate(2, Date.valueOf(endDate));
    }

}