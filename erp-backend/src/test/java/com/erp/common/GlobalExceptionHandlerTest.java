package com.erp.common;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correctifs de cette session : les messages d'exception internes (SQL, chemins, noms de
 * classes) ne doivent plus fuiter au client (handleGeneric), et un conflit de verrou optimiste
 * (deux modifications concurrentes sur la même entité versionnée — AccountMove, StockQuant)
 * doit remonter en 409 avec un message clair plutôt qu'un 500 générique.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimisticLockConflictReturns409WithAUserFriendlyMessage() {
        ResponseEntity<?> response = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("Row was updated or deleted by another transaction"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().toString()).doesNotContain("transaction");
    }

    @Test
    void genericExceptionNeverLeaksTheRawExceptionMessage() {
        ResponseEntity<?> response = handler.handleGeneric(
                new RuntimeException("password=hunter2 at jdbc:postgresql://internal-host:5432/db"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString())
                .doesNotContain("hunter2")
                .doesNotContain("internal-host")
                .doesNotContain("jdbc");
    }

    @Test
    void entityNotFoundReturns404WithItsOwnMessage() {
        ResponseEntity<?> response = handler.handleNotFound(new EntityNotFoundException("Ressource introuvable"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().toString()).contains("Ressource introuvable");
    }
}
