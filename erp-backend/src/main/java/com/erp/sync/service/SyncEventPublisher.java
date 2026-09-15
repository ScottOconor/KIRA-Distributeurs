package com.erp.sync.service;

import com.erp.sync.dto.SpokeSnapshotPayload;
import com.erp.sync.entity.OutboxEvent;
import com.erp.sync.entity.OutboxStatus;
import com.erp.sync.entity.SyncEventType;
import com.erp.sync.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncEventPublisher {

    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    @Value("${sync.spoke.id}")
    private String spokeId;

    /**
     * Publie un snapshot dans une nouvelle transaction indépendante.
     * REQUIRES_NEW évite d'hériter de la transaction readOnly du SnapshotService.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishSnapshot(SpokeSnapshotPayload snapshot) {
        publish(SyncEventType.FULL_SNAPSHOT, "SNAPSHOT_" + snapshot.getSpokeId(), snapshot);
    }

    /**
     * Enregistre un événement métier dans l'outbox pour envoi différé au Hub.
     * Doit être appelé dans la même transaction que l'opération métier.
     */
    public void publish(SyncEventType eventType, String entityId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Ne JAMAIS avaler cette erreur : publish() s'exécute dans la même transaction que
            // l'opération métier (garantie outbox transactionnelle) — logger et continuer laisserait
            // l'écriture métier se valider avec un événement outbox silencieusement jamais créé, donc
            // jamais renvoyé au Hub. Faire échouer toute la transaction est le seul moyen de préserver
            // l'atomicité "tout ou rien" attendue du pattern outbox.
            log.error("Impossible de sérialiser le payload pour l'événement {} id={} : {}", eventType, entityId, e.getMessage());
            throw new IllegalStateException("Échec de sérialisation de l'événement de synchronisation " + eventType, e);
        }
        OutboxEvent event = new OutboxEvent();
        event.setSpokeId(spokeId);
        event.setEventType(eventType);
        event.setEntityId(entityId);
        event.setPayload(json);
        event.setStatus(OutboxStatus.PENDING);
        outboxRepo.save(event);
        log.debug("Outbox event enregistré: {} id={}", eventType, entityId);
    }
}
