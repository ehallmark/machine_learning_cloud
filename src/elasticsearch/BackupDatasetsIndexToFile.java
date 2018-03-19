package elasticsearch;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BackupDatasetsIndexToFile {
    public static final File backupFile = new File(Constants.DATA_FOLDER+"elasticsearch_dataset_index.es");
    public static void main(String[] args) {
        // this program will backup the dataset index to a file
        TransportClient client = MyClient.get();
        Map<String,Map<String,Object>> data = Collections.synchronizedMap(new HashMap<>());
        SearchResponse response = client.prepareSearch(DatasetIndex.INDEX)
                .setTypes(DatasetIndex.TYPE)
                .setFetchSource(true)
                .setSize(10000)
                .setFrom(0)
                .setScroll(new TimeValue(120000))
                .get();
        do {
            SearchHits searchHits = response.getHits();
            SearchHit[] hits = searchHits.getHits();
            for(int i = 0; i < hits.length; i++) {
                SearchHit hit = hits[i];
                data.put(hit.getId(),hit.getSource());
            }
            response = client.prepareSearchScroll(response.getScrollId()).setScroll(new TimeValue(120000)).execute().actionGet();
        }
        while (response != null && response.getHits().getHits().length != 0); // Zero hits mark the end of the scroll and the while loop.

        Database.trySaveObject(data,backupFile);
    }
}
