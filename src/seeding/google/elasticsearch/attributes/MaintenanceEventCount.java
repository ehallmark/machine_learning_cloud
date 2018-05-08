package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class MaintenanceEventCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.MAINTENANCE_EVENT_COUNT;
    }
}
