package com.erp.stock.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductDTO {
    private Long id;
    private String defaultCode;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String uomName;
    private Long unitOfMeasureId;
    private BigDecimal standardPrice;
    private BigDecimal salePrice;
    private String type;
    private String stockAccountCode;
    private String description;
    private boolean active;
    private boolean exemptTva;
    private boolean exemptTvaAchat;
    private Long companyId;
    // Vrai si une photo est enregistrée (évite d'envoyer le binaire dans les listes)
    private boolean hasPhoto;
    // Stock (calculé)
    private BigDecimal qtyOnHand;
    private BigDecimal qtyReserved;
    private BigDecimal qtyAvailable;
    private LocalDateTime createdAt;
}
