package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class MaintenanceEvent extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.MAINTENANCE_EVENT;
    }
}
