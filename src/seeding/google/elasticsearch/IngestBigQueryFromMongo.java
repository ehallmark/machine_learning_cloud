package seeding.google.elasticsearch;

import elasticsearch.IngestMongoIntoElasticSearch;
import seeding.google.mongo.ingest.IngestPatents;

public class IngestBigQueryFromMongo {

    public static void main(String[] args) {
        final String index = IngestPatents.INDEX_NAME;
        final String type = IngestPatents.TYPE_NAME;

        String[] fields = Attributes.buildAttributes().stream().map(a->a.getName()).toArray(s->new String[s]);

        IngestMongoIntoElasticSearch.ingestByType(index,type,false,fields);
    }
}
