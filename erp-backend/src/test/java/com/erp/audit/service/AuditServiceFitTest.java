package com.erp.audit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Les valeurs d'audit trop longues sont tronquées à la taille de leur colonne au lieu de faire
 *  échouer l'écriture (FISCAL-01 : libellé de clôture d'exercice de ~100 caractères pour 80). */
class AuditServiceFitTest {

    @Test
    void valeurCourteInchangee() {
        assertThat(AuditService.fit("Clôture d'exercice", 80)).isEqualTo("Clôture d'exercice");
        assertThat(AuditService.fit(null, 80)).isNull();
    }

    @Test
    void valeurLongueTronqueeALaColonne() {
        String label = "Clôture d'exercice du 2026-01-01 au 2026-12-31 — écriture CLO-2026-00001 (brouillon), résultat net = 1234567.89";
        String fitted = AuditService.fit(label, 80);
        assertThat(fitted).hasSize(80).endsWith("…").startsWith("Clôture d'exercice du 2026-01-01");
    }
}
