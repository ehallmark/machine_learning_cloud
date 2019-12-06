package api;
import com.google.gson.Gson;
import spark.Request;
import spark.Response;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static spark.Spark.*;
import static j2html.TagCreator.*;

public class API {
    private static class APIHandler {
        private Connection connection;
        private Lock lock;
        private String connectionString;

        private APIHandler(String connectionString) {
            this.connectionString = connectionString;
            this.lock = new ReentrantLock();
            this.resetConnection();
        }

        private void resetConnection() {
            this.lock.lock();
            try {
                try {
                    this.connection.close();
                } catch (Exception e) {
                    // pass
                }
                this.connection = null;
                this.connection = DriverManager.getConnection(connectionString);
                this.connection.setAutoCommit(true);

            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                this.lock.unlock();
            }

        }

        public List<Map<String, Object>> runQuery(String query, List<String> headers, Consumer<PreparedStatement> statementConsumer, int retries) {
            for (int retry = 0; retry < retries; retry ++) {
                final List<Map<String, Object>> data = new ArrayList<>(10);
                boolean success = true;
                this.lock.lock();
                try {
                    PreparedStatement ps = this.connection.prepareStatement(query);
                    statementConsumer.accept(ps);
                    ResultSet rs = ps.executeQuery();
                    Map<String, Object> item = new HashMap<>(headers.size());
                    while (rs.next()) {
                        for (int i = 0; i < headers.size(); i++) {
                            String header = headers.get(i);
                            item.put(header, rs.getObject(i + 1));
                        }
                    }
                    data.add(item);

                    rs.close();
                    ps.close();

                } catch (Exception e) {
                    e.printStackTrace();
                    success = false;
                } finally {
                    this.lock.unlock();
                }
                if (success) {
                    return data;
                } else {
                    System.out.println("Resetting...");
                    this.resetConnection();
                }
            }
            return Collections.emptyList();
        }
    }
    private static final String PATENTDB_URL = "jdbc:postgresql://10.20.20.15/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private static final String PLI_URL = "jdbc:postgresql://10.20.20.15/pli_production?user=postgres&password=&tcpKeepAlive=true";
    private static final String TICKER_URL = "jdbc:postgresql://10.20.20.15/ticker_manager?user=postgres&password=&tcpKeepAlive=true";

    private static final APIHandler PMI_HANDLER = new APIHandler(PATENTDB_URL);
    private static final APIHandler PLI_HANDLER = new APIHandler(PLI_URL);
    private static final APIHandler SP500_HANDLER = new APIHandler(PLI_URL);
    private static final APIHandler TICKER_HANDLER = new APIHandler(TICKER_URL);

    private static final List<String> PMI_HEADERS = Arrays.asList("id", "end_date", "value");
    private static final List<String> SP500_HEADERS = Arrays.asList("id", "asset_id", "date", "price", "created_id", "updated_at");
    private static final List<String> PLI_HEADERS = Arrays.asList("id", "date", "price", "basket_id", "created_at", "updated_at");
    private static final List<String> TICKER_HEADERS = Arrays.asList("id", "buyer", "seller", "asset_size", "date_recorded", "reel", "frame", "created_at", "updated_at");

    private static List<Map<String, Object>> latestPMIData(int offset, int limit) {
        Consumer<PreparedStatement> psHandler = ps -> {
            try {
                ps.setInt(1, offset);
                ps.setInt(2, limit);
            } catch(Exception e) {
                e.printStackTrace();
            }
        };

        String sql = "select id, end_date, value from pmi_90_day_moving_average order by end_date desc offset ? limit ?";

        return PMI_HANDLER.runQuery(sql, PMI_HEADERS, psHandler, 3);
    }

    private static List<Map<String, Object>> latestPLIData(int offset, int limit) {
        Consumer<PreparedStatement> psHandler = ps -> {
            try {
                ps.setInt(1, offset);
                ps.setInt(2, limit);
            } catch(Exception e) {
                e.printStackTrace();
            }
        };

        String sql = "select id, date, price, basket_id, created_at, updated_at from patent_licensing_indices order by date desc offset ? limit ?";

        return PLI_HANDLER.runQuery(sql, PLI_HEADERS, psHandler, 3);
    }

    private static List<Map<String, Object>> latestSP500Data(int offset, int limit) {
        Consumer<PreparedStatement> psHandler = ps -> {
            try {
                ps.setInt(1, offset);
                ps.setInt(2, limit);
            } catch(Exception e) {
                e.printStackTrace();
            }
        };

        String sql = "select id, asset_id, date, price, created_at, updated_at from quotes where asset_id=9 order by date desc offset ? limit ?";

        return SP500_HANDLER.runQuery(sql, SP500_HEADERS, psHandler, 3);
    }

    private static List<Map<String, Object>> latestTickerData(int offset, int limit) {
        Consumer<PreparedStatement> psHandler = ps -> {
            try {
                ps.setInt(1, offset);
                ps.setInt(2, limit);
            } catch(Exception e) {
                e.printStackTrace();
            }
        };

        String sql = "select id, buyer, seller, asset_size, date_recorded, reel, frame, created_at, updated_at from transactions order by date_recorded desc offset ? limit ?";

        return TICKER_HANDLER.runQuery(sql, TICKER_HEADERS, psHandler, 3);
    }


    private static int handleLimit(Request req) {
        int val;
        try {
            val = Integer.valueOf(req.queryParams("limit"));
        } catch(Exception e) {
            val = 10;
        }
        return Math.min(100, val);
    }

    private static int handleOffset(Request req) {
        int val;
        try {
            val = Integer.valueOf(req.queryParams("offset"));
        } catch(Exception e) {
            val = 0;
        }
        return val;
    }

    private static String handleRequest(Request req, Response res) {
        final String resource = req.params("resource");
        final Object data;
        int limit = handleLimit(req);
        int offset = handleOffset(req);
        switch (resource.toLowerCase()) {
            case "pmi": {
                data = latestPMIData(offset, limit);
                break;
            }
            case "pli": {
                data = latestPLIData(offset, limit);
                break;
            }
            case "sp500": {
                data = latestSP500Data(offset, limit);
                break;
            }
            case "ticker": {
                data = latestTickerData(offset, limit);
                break;
            }
            default: {
                data = Collections.emptyList();
            }
        }
        return resultsFormatter(resource, data);
    }

    public static String resultsFormatter(String resource, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        result.put("timestamp", System.currentTimeMillis());
        result.put("resource", resource);
        return new Gson().toJson(result);
    }

    public static void main(String[] args) throws Exception {
        String path = "/api/:resource/latest";
        get(path, API::handleRequest);
        post(path, API::handleRequest);
    }
}
