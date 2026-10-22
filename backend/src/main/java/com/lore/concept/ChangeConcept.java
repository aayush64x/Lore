package com.lore.concept;

import com.lore.change.Change;
import jakarta.persistence.*;
import java.util.UUID;

/** One concept a change was tagged with. other = a name the AI suggested, not on the list. */
@Entity
@Table(name = "change_concepts")
public class ChangeConcept {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false)
    private Change change;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean other;

    protected ChangeConcept() {}

    public ChangeConcept(Change change, String name, boolean other) {
        this.id = UUID.randomUUID();
        this.change = change;
        this.name = name;
        this.other = other;
    }

    public Change getChange() { return change; }
    public String getName() { return name; }
    public boolean isOther() { return other; }
}
