package models.genetics.lead_development;

import user_interface.server.SimilarPatentServer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/25/2017.
 */
public abstract class Attribute {
    public String name;
    public String humanName;
    public double importance;
    private final int id;
    static final AtomicInteger idCounter = new AtomicInteger(0);
    public Attribute(String name, double importance) {
        this(name,importance,idCounter.getAndIncrement());
    }

    protected Attribute(String name, double importance, int id) {
        this.name=name;
        this.humanName= SimilarPatentServer.humanAttributeFor(name);
        this.importance=importance;
        this.id=id;
    }

    public int getId() {
        return id;
    }

    public abstract Attribute dup();

    @Override
    public boolean equals(Object other) {
        return other.hashCode()==hashCode();
    }

    public abstract double scoreAssignee(String assignee);

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
