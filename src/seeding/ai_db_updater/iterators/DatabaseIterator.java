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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**

 */
public class DatabaseIterator {
    private static boolean debug = true;
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

    public void run() throws SQLException {
        Database.setupSeedConn();
        this.init();

        Map<String,String> pgNamesToAttrNames = topLevelAttributes.stream().filter(attr->Constants.PG_NAME_MAP.containsKey(attr.getFullName())).collect(Collectors.toMap(attr->Constants.PG_NAME_MAP.get(attr),attr->attr.getFullName()));
        List<String> pgNames = new ArrayList<>(pgNamesToAttrNames.keySet());
        List<String> javaNames = pgNames.stream().map(pg->pgNamesToAttrNames.get(pg)).collect(Collectors.toList());

        StringJoiner selectJoin = new StringJoiner(",","select ", " from ");
        pgNames.forEach(name->selectJoin.add(name));

        String patentDBQuery = selectJoin.toString() + " patentdb where pub_date::date >= ? and pub_date::date < ?";
        PreparedStatement patentDBStatement = Database.seedConn.prepareStatement(patentDBQuery);
        patentDBStatement.setDate(1, Date.valueOf(startDate));
        patentDBStatement.setDate(2, Date.valueOf(endDate));

        patentDBStatement.setFetchSize(10);

        ResultSet rs = patentDBStatement.executeQuery();
        while(rs.next()) {
            Map<String,Object> data = new HashMap<>();
            IntStream.range(0,pgNames.size()).forEach(i->{
                try {
                    Object value = rs.getObject(i + 1);
                    if(value!=null) {
                        if (debug) {
                            System.out.println("Value type of " + pgNames.get(i) + ": " + (value.getClass().getName()));
                        }
                        data.put(javaNames.get(i), value);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });
            Object patent = data.get(Constants.NAME);
            Object filing = data.get(Constants.FILING_NAME);
            if(patent!=null&&filing!=null) {
                DataIngester.ingestBulk(patent.toString(), filing.toString(), data, true);
            }
        }

        patentDBStatement.close();
    }

}