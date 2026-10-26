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

    @Value("${sync.snapshot.part-bytes:8388608}")
    private int snapshotPartBytes;

    /** Seuil d'alerte avant la limite réelle du broker (16 Mo) — laisse une marge pour repérer
     *  la croissance avant le rejet 406 PRECONDITION_FAILED plutôt que de le découvrir en prod
     *  (cf. incidents 2026-09-14/16 sur des listes non fenêtrées du snapshot horaire). */
    private static final int PAYLOAD_WARN_BYTES = 8 * 1024 * 1024;

    // Explicit Function constants to avoid ambiguous method-reference target typing
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> SALE_INVOICES = SpokeSnapshotPayload::getSaleInvoices;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> ACCOUNT_MOVE_LINES = SpokeSnapshotPayload::getAccountMoveLines;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> PRODUCTS = SpokeSnapshotPayload::getProducts;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> PARTNERS = SpokeSnapshotPayload::getPartners;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> SALES_ORDERS = SpokeSnapshotPayload::getSalesOrders;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> PURCHASE_INVOICES = SpokeSnapshotPayload::getPurchaseInvoices;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> PURCHASE_ORDERS = SpokeSnapshotPayload::getPurchaseOrders;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> RISTOURNE_PAIEMENTS = SpokeSnapshotPayload::getRistournePaiements;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> REMISE_PAIEMENTS = SpokeSnapshotPayload::getRemisePaiements;
    private static final java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>> ACCOUNTS = SpokeSnapshotPayload::getAccounts;

    /**
     * Publie un snapshot dans une nouvelle transaction indépendante.
     * REQUIRES_NEW évite d'hériter de la transaction readOnly du SnapshotService.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishSnapshot(SpokeSnapshotPayload snapshot) {
        // Fractionne le snapshot en plusieurs parties si nécessaire pour rester sous la limite
        // cible par partie (par défaut 8MB) et publie chaque partie séparément.
        try {
            var parts = splitSnapshotBySize(snapshot, snapshotPartBytes);
            int total = parts.size();
            for (int i = 0; i < total; i++) {
                var part = parts.get(i);
                String entityId = String.format("SNAPSHOT_%s_part_%d_of_%d", snapshot.getSpokeId(), i + 1, total);
                publish(SyncEventType.FULL_SNAPSHOT, entityId, part);
            }
        } catch (Exception e) {
            log.error("Erreur fractionnement snapshot {}: {}", snapshot.getSpokeId(), e.getMessage(), e);
            // Fallback : publier en une seule partie (expose possible rejet côté broker)
            publish(SyncEventType.FULL_SNAPSHOT, "SNAPSHOT_" + snapshot.getSpokeId(), snapshot);
        }
    }

    /**
     * Découpe un `SpokeSnapshotPayload` en parties dont la taille JSON sérialisée ne dépasse pas
     * `maxBytes` (sauf élément isolé plus gros que la limite). Chaque partie reprend tous les
     * indicateurs scalaires et petites listes du snapshot ; les grosses listes sont réparties dans
     * l'ordre, sans perte ni doublon.
     *
     * Linéaire : la taille de chaque élément est calculée une seule fois. L'algorithme précédent
     * recopiait la liste ET resérialisait toute la partie en cours à chaque élément ajouté
     * (quadratique) : un snapshot de ~200 000 éléments n'était pas terminé après 12 minutes de
     * calcul, relancé toutes les heures.
     */
    java.util.List<SpokeSnapshotPayload> splitSnapshotBySize(SpokeSnapshotPayload snapshot, int maxBytes) throws JsonProcessingException {
        // Base de chaque partie = copie INTÉGRALE du snapshot (toBuilder) : tous les indicateurs
        // scalaires et les petites listes affichées telles quelles par le Hub (caisses,
        // caissesActuelles, topStock, ventes du jour) sont présents dans chaque partie, et seules
        // les grosses listes découpables repartent vides. Avant : une liste blanche recopiée champ
        // par champ, qui avait oublié les détails ristournes/remises (collecte, paiement) et frais
        // d'enlèvement (année/mois/trimestre) ainsi que les caisses — le Hub, qui relit chaque
        // partie, les recevait à null et les écrasait à 0 à chaque snapshot.
        java.util.function.Supplier<SpokeSnapshotPayload.SpokeSnapshotPayloadBuilder> baseBuilder = () -> snapshot.toBuilder()
                .partners(new java.util.ArrayList<>())
                .products(new java.util.ArrayList<>())
                .accounts(new java.util.ArrayList<>())
                .saleInvoices(new java.util.ArrayList<>())
                .purchaseInvoices(new java.util.ArrayList<>())
                .accountMoveLines(new java.util.ArrayList<>())
                .salesOrders(new java.util.ArrayList<>())
                .purchaseOrders(new java.util.ArrayList<>())
                .ristournePaiements(new java.util.ArrayList<>())
                .remisePaiements(new java.util.ArrayList<>());

        java.util.List<java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>>> listGetters = java.util.List.of(
                SALE_INVOICES, ACCOUNT_MOVE_LINES, PRODUCTS, PARTNERS, SALES_ORDERS, PURCHASE_INVOICES, PURCHASE_ORDERS,
                RISTOURNE_PAIEMENTS, REMISE_PAIEMENTS, ACCOUNTS);

        // Taille d'une partie = base (listes vides) + somme des éléments + une virgule par élément :
        // majorant exact de la taille sérialisée réelle, donc jamais au-dessus de maxBytes.
        int baseBytes = objectMapper.writeValueAsBytes(baseBuilder.get().build()).length;
        java.util.List<SpokeSnapshotPayload> parts = new java.util.ArrayList<>();
        java.util.Map<java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>>, java.util.List<Object>> current = new java.util.LinkedHashMap<>();
        long currentBytes = baseBytes;
        int currentCount = 0;

        for (var getter : listGetters) {
            java.util.List<?> items = getter.apply(snapshot);
            if (items == null) continue;
            for (Object item : items) {
                int itemBytes = objectMapper.writeValueAsBytes(item).length + 1;
                if (currentCount > 0 && currentBytes + itemBytes > maxBytes) {
                    parts.add(buildPart(baseBuilder.get(), current));
                    current = new java.util.LinkedHashMap<>();
                    currentBytes = baseBytes;
                    currentCount = 0;
                }
                if (baseBytes + itemBytes > maxBytes) {
                    log.warn("Un élément individuel du snapshot dépasse la taille max {} bytes; acceptez une partie plus grande", maxBytes);
                }
                current.computeIfAbsent(getter, g -> new java.util.ArrayList<>()).add(item);
                currentBytes += itemBytes;
                currentCount++;
            }
        }
        // Dernière partie : gardée si elle porte des éléments, ou si c'est la seule (snapshot sans liste).
        if (currentCount > 0 || parts.isEmpty()) parts.add(buildPart(baseBuilder.get(), current));
        return parts;
    }

    @SuppressWarnings("unchecked")
    private static SpokeSnapshotPayload buildPart(SpokeSnapshotPayload.SpokeSnapshotPayloadBuilder builder,
            java.util.Map<java.util.function.Function<SpokeSnapshotPayload, java.util.List<?>>, java.util.List<Object>> lists) {
        lists.forEach((getter, items) -> {
            java.util.List raw = items;
            if (getter == SALE_INVOICES) builder.saleInvoices(raw);
            else if (getter == ACCOUNT_MOVE_LINES) builder.accountMoveLines(raw);
            else if (getter == PRODUCTS) builder.products(raw);
            else if (getter == PARTNERS) builder.partners(raw);
            else if (getter == SALES_ORDERS) builder.salesOrders(raw);
            else if (getter == PURCHASE_INVOICES) builder.purchaseInvoices(raw);
            else if (getter == PURCHASE_ORDERS) builder.purchaseOrders(raw);
            else if (getter == RISTOURNE_PAIEMENTS) builder.ristournePaiements(raw);
            else if (getter == REMISE_PAIEMENTS) builder.remisePaiements(raw);
            else if (getter == ACCOUNTS) builder.accounts(raw);
        });
        return builder.build();
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
        int payloadBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (payloadBytes >= PAYLOAD_WARN_BYTES) {
            log.warn("Payload outbox volumineux ({} Mo) pour {} id={} — approche de la limite du "
                    + "broker (16 Mo), vérifier les fenêtres de date des listes du snapshot",
                    String.format("%.1f", payloadBytes / (1024.0 * 1024.0)), eventType, entityId);
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
