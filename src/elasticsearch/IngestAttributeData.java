package elasticsearch;

import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 7/23/2017.
 */
public class IngestAttributeData {
    private static final int batchSize = 5000;
    public static void main(String[] args) {
        SimilarPatentServer.initialize();
        SimilarPatentServer.loadAllItems();
        ingest(SimilarPatentServer.getAllApplications(), PortfolioList.Type.applications, false);
        ingest(SimilarPatentServer.getAllPatents(), PortfolioList.Type.patents, false);
        ingest(SimilarPatentServer.getAllAssignees(), PortfolioList.Type.assignees, true);
    }

    public static void ingest(List<Item> items, PortfolioList.Type type, boolean index) {
        AtomicInteger cnt = new AtomicInteger(0);
        chunked(items).parallelStream().forEach(itemList->{
            DataIngester.ingestItems(itemList,type,index);
            cnt.getAndAdd(batchSize);
            System.out.println("Seen "+cnt.get()+" "+type.toString());
        });
    }

    private static List<Collection<Item>> chunked(List<Item> items) {
        List<Collection<Item>> chunks = new ArrayList<>((items.size()+1)/batchSize);
        for(int i = 0; i < items.size(); i+= batchSize) {
            List<Item> chunk = new ArrayList<>(batchSize);
            for(int j = i; j < (Math.min(i+ batchSize, items.size())); j++) {
                chunk.add(items.get(j));
            }
            if(chunk.size() > 0) chunks.add(chunk);
        }
        return chunks;
    }
}
