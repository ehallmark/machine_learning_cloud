package seeding.google.elasticsearch;

import elasticsearch.IngestMongoIntoElasticSearch;
import seeding.google.attributes.Constants;
import seeding.google.mongo.IngestPatents;

public class IngestBigQueryFromMongo {

    public static void main(String[] args) {
        final String index = IngestPatents.INDEX_NAME;
        final String type = IngestPatents.TYPE_NAME;

        String[] fields = Constants.buildAttributes().stream().map(a->a.getName()).toArray(s->new String[s]);

        IngestMongoIntoElasticSearch.ingestByType(index,type,false,fields);
    }
}
