package com.lore.concept;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChangeConceptRepository extends JpaRepository<ChangeConcept, UUID> {

    /** With each tag's change loaded, so it can be read outside a transaction. */
    @Query("SELECT cc FROM ChangeConcept cc JOIN FETCH cc.change ch WHERE ch.session.id = :sessionId")
    List<ChangeConcept> findByChangeSessionId(@Param("sessionId") UUID sessionId);
}
