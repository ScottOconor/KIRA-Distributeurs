package com.erp.sync.repository;

import com.erp.sync.entity.OutboxEvent;
import com.erp.sync.entity.OutboxStatus;
import com.erp.sync.entity.SyncEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Utilisé pour ne pas renvoyer un snapshot complet quand rien ne s'est produit depuis le précédent. */
    long countByCreatedAtAfter(LocalDateTime since);

    /**
     * Supprime une contrainte CHECK héritée sur event_type (ex : sync_outbox_events_event_type_check)
     * qui figeait la liste des valeurs autorisées à un instant donné du schéma. L'entité {@link
     * OutboxEvent#getEventType()} ne déclare aucune contrainte de ce type (simple varchar(100)) et
     * {@link SyncEventType} gagne régulièrement de nouvelles valeurs — toute contrainte CHECK figée
     * en base finit donc par rejeter les nouveaux types d'événements (ex: RISTOURNE_PAIEMENT_POSTED)
     * avec une erreur SQL peu explicite au moment de l'insertion. Idempotent (IF EXISTS).
     */
    @Modifying
    @Query(value = "ALTER TABLE sync_outbox_events DROP CONSTRAINT IF EXISTS sync_outbox_events_event_type_check",
           nativeQuery = true)
    void dropStaleEventTypeCheckConstraint();

    // Tri par id en second critère : deux événements insérés dans la MÊME transaction métier
    // (ex. ACCOUNT_MOVE_FROM_SALE puis SALE_INVOICE_POSTED) reçoivent des createdAt indépendants,
    // sans garantie d'ordre stable en cas d'égalité — l'id d'insertion (auto-incrémenté) donne
    // l'ordre réel, déterministe, quelle que soit la résolution de l'horloge JVM.
    List<OutboxEvent> findByStatusOrderByCreatedAtAscIdAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    Page<OutboxEvent> findByEventTypeInOrderByCreatedAtDesc(List<SyncEventType> types, Pageable pageable);

    Page<OutboxEvent> findByStatusAndEventTypeInOrderByCreatedAtDesc(OutboxStatus status, List<SyncEventType> types, Pageable pageable);

    @Query("SELECT e.status, COUNT(e) FROM OutboxEvent e WHERE e.eventType IN :types GROUP BY e.status")
    List<Object[]> countByStatusForTypes(@Param("types") List<SyncEventType> types);

    @Query("SELECT e.status, COUNT(e) FROM OutboxEvent e GROUP BY e.status")
    List<Object[]> countByStatusGrouped();

    /** Supprime définitivement tous les événements en échec — action destructive assumée par
     *  l'utilisateur (bouton "Vider les échecs"), à n'utiliser qu'après avoir résolu la cause des
     *  échecs et accepté de perdre ces opérations plutôt que de les revoir un jour au Hub. */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status")
    int deleteByStatus(@Param("status") OutboxStatus status);

    /** Purge les événements SENT plus vieux que la date donnée — une fois envoyés, ils n'ont plus
     *  aucune utilité opérationnelle (le Hub en a déjà sa propre copie) ; les laisser s'accumuler
     *  indéfiniment ne fait que grossir la table sans raison (bloat, autovacuum, requêtes plus
     *  lentes sur sync_outbox_events). PENDING et FAILED ne sont jamais concernés par cette purge.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.createdAt < :before")
    int deleteByStatusAndCreatedAtBefore(@Param("status") OutboxStatus status, @Param("before") LocalDateTime before);
}
