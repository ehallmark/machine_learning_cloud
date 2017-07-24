package elasticsearch;

import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 7/23/2017.
 */
public class IngestAttributeData {
    public static void main(String[] args) {
        SimilarPatentServer.initialize();
        ingest(SimilarPatentServer.getAllApplications(), PortfolioList.Type.applications);
        ingest(SimilarPatentServer.getAllPatents(), PortfolioList.Type.patents);
        ingest(SimilarPatentServer.getAllAssignees(), PortfolioList.Type.assignees);
    }

    private static void ingest(Collection<Item> items, PortfolioList.Type type) {
        AtomicInteger cnt = new AtomicInteger(0);
        items.parallelStream().forEach(item->{
            DataIngester.ingestItem(item,type);
            if(cnt.getAndIncrement()%10000==0) {
                System.out.println("Seen "+cnt.get()+" "+type.toString());
            }
        });
    }
}
