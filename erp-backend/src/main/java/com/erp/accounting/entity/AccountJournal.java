package com.erp.accounting.entity;

import com.erp.common.entity.Company;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "account_journals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /**
     * Type: sale / purchase / cash / bank / general
     */
    private String type;

    /**
     * Moyen de paiement rattaché à ce journal (ex : Espèces, Carte bancaire, Mobile Money,
     * Chèque, Virement) — utilisé pour le proposer lors du paiement d'une facture (point de
     * vente notamment) sans exposer le nom technique du journal comptable. Pertinent surtout
     * pour les journaux de liquidité (cash/bank).
     */
    private String paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_debit_account_id")
    private AccountAccount defaultDebitAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_credit_account_id")
    private AccountAccount defaultCreditAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    private boolean active;
}
