package test;

import elasticsearch.IngestAttributeData;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateElasticSearch {

    public static void main(String[] args) {
        //PatentGrantIterator patentIterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        //patentIterator.applyHandlers(new ElasticSearchHandler(PortfolioList.Type.patents));
        //PatentGrantIterator applicationIterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        //applicationIterator.applyHandlers(new ElasticSearchHandler(PortfolioList.Type.applications));

        IngestAttributeData.main(args);
    }
}
