package seeding.google.elasticsearch;

import elasticsearch.IngestMongoIntoElasticSearch;
import seeding.google.elasticsearch.attributes.ConvenienceAttribute;
import seeding.google.mongo.ingest.IngestPatents;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.stream.Stream;

public class IngestBigQueryFromMongo {

    public static void main(String[] args) {
        final String index = IngestPatents.INDEX_NAME;
        final String type = IngestPatents.TYPE_NAME;

        String[] fields = Attributes.buildAttributes().stream()
                .flatMap(attr->attr instanceof NestedAttribute ? ((NestedAttribute) attr).getAttributes().stream() : Stream.of(attr))
                .filter(attr->!(attr instanceof ConvenienceAttribute))
                .map(a->a.getName()).toArray(s->new String[s]);

        IngestMongoIntoElasticSearch.ingestByType(index,type,false,fields);
    }
}
