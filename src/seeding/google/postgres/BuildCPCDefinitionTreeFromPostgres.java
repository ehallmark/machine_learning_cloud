package seeding.google.postgres;

import graphical_modeling.model.graphs.BayesianNet;
import graphical_modeling.model.graphs.Graph;
import graphical_modeling.model.nodes.Node;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.ingest.IngestJsonHelper;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class BuildCPCDefinitionTreeFromPostgres extends IngestPatentsFromJson {

    public static void main(String[] args) throws SQLException {
        String[] fields = new String[]{
                Constants.CODE,
                Constants.TREE
        };

        Connection conn = Database.getConn();

        String valueStr = "(?,?)";
        String conflictStr = "(?)";
        final String sql = "insert into big_query_cpc_definition_tree (code,tree) values "+valueStr+" on conflict (code) do update set (tree) = "+conflictStr;

        DefaultApplier applier = new DefaultApplier(true, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);

        Consumer<Document> consumer = doc -> {
            try {
                List<Object> data = new ArrayList<>(fields.length);
                for(int i = 0; i < fields.length; i++) {
                    Object val = doc.get(fields[i]);
                    if(i==0&&val==null) {
                        System.out.println("Missing code!");
                        return;
                    }
                    data.add(val);
                }
                queryStream.ingest(data);
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        };

        PreparedStatement ps = conn.prepareStatement("select code,parents,children from big_query_cpc_definition");
        ResultSet rs = ps.executeQuery();

        List<String> codes = new ArrayList<>();
        Graph graph = new BayesianNet();
        while(rs.next()) {
            String code = rs.getString(1);
            String[] parents = (String[])rs.getArray(2).getArray();
            String[] children = (String[])rs.getArray(3).getArray();
            Node n = graph.addBinaryNode(code);
            codes.add(code);
            if(parents != null) {
                for(String parent : parents) {
                    Node n2 = graph.addBinaryNode(parent);
                    graph.connectNodes(n2,n);
                }
            }
            if(children != null) {
                for(String child : children) {
                    Node n2 = graph.addBinaryNode(child);
                    graph.connectNodes(n,n2);
                }
            }
        }
        rs.close();
        ps.close();

        for(String code : codes) {
            Document doc = new Document();
            doc.put(Constants.CODE,code);
            Node node = graph.findNode(code);
            List<String> tree = new ArrayList<>();
            while(node!=null) {
                tree.add(node.getLabel());
                Collection<Node> parents = node.getParents();
                if(parents!=null&&parents.size()>1) {
                    throw new RuntimeException("Node has multiple parents: "+node.getLabel());
                }
                if(parents!=null&&parents.size()==1) {
                    node = node.getParents().get(0);
                }
            }
            doc.put(Constants.TREE,tree);
            consumer.accept(doc);
        }

        queryStream.close();
        conn.close();
    }

}
