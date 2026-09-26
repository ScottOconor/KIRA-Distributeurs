package com.erp.stock.service;

import com.erp.auth.TenantAuthenticationDetails;
import com.erp.stock.dto.ProductCategoryDTO;
import com.erp.stock.entity.ProductCategory;
import com.erp.stock.repository.ProductCategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Création de catégorie d'article : le companyId est dérivé du JWT (jamais du DTO client), les
 * comptes OHADA reçoivent leur valeur par défaut, et le parent est résolu pour l'affichage.
 */
@ExtendWith(MockitoExtension.class)
class StockServiceCategoryTest {

    @Mock private ProductCategoryRepository categoryRepo;

    @InjectMocks
    private StockService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsCompany(Long companyId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
        auth.setDetails(new TenantAuthenticationDetails(companyId, "10.0.0.1"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void createCategoryUsesCompanyIdFromSecurityContextAndDefaultAccounts() {
        authenticateAsCompany(7L);
        when(categoryRepo.save(any(ProductCategory.class))).thenAnswer(inv -> {
            ProductCategory c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        // Le companyId du DTO (99) doit être ignoré au profit de celui du JWT (7).
        ProductCategoryDTO result = service.createCategory(
                ProductCategoryDTO.builder().name("Boissons").code("BOI").companyId(99L).build());

        ArgumentCaptor<ProductCategory> saved = ArgumentCaptor.forClass(ProductCategory.class);
        verify(categoryRepo).save(saved.capture());
        assertThat(saved.getValue().getCompanyId()).isEqualTo(7L);
        assertThat(saved.getValue().getStockAccountCode()).isEqualTo("311000");
        assertThat(saved.getValue().getStockInAccountCode()).isEqualTo("603100");
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("Boissons");
        assertThat(result.getCompanyId()).isEqualTo(7L);
    }

    @Test
    void createCategoryKeepsExplicitAccountCodes() {
        authenticateAsCompany(1L);
        when(categoryRepo.save(any(ProductCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductCategoryDTO result = service.createCategory(ProductCategoryDTO.builder()
                .name("Pièces").stockAccountCode("312000").stockInAccountCode("603200").build());

        assertThat(result.getStockAccountCode()).isEqualTo("312000");
        assertThat(result.getStockInAccountCode()).isEqualTo("603200");
    }

    @Test
    void createCategoryResolvesParentName() {
        authenticateAsCompany(1L);
        when(categoryRepo.save(any(ProductCategory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepo.findById(5L)).thenReturn(
                Optional.of(ProductCategory.builder().id(5L).name("Parent").companyId(1L).build()));

        ProductCategoryDTO result = service.createCategory(
                ProductCategoryDTO.builder().name("Enfant").parentId(5L).build());

        assertThat(result.getParentId()).isEqualTo(5L);
        assertThat(result.getParentName()).isEqualTo("Parent");
    }

    @Test
    void createCategoryWithoutSecurityContextFailsExplicitly() {
        assertThatThrownBy(() -> service.createCategory(ProductCategoryDTO.builder().name("X").build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createCategoryWithoutTenantDetailsSavesNullCompanyId() {
        // Cas limite documenté : sans TenantAuthenticationDetails, companyId est null et la
        // colonne (nullable=false) fera échouer l'INSERT en base -> 409 via GlobalExceptionHandler.
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
        auth.setDetails("autre");
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(categoryRepo.save(any(ProductCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createCategory(ProductCategoryDTO.builder().name("X").build());

        ArgumentCaptor<ProductCategory> saved = ArgumentCaptor.forClass(ProductCategory.class);
        verify(categoryRepo).save(saved.capture());
        assertThat(saved.getValue().getCompanyId()).isNull();
    }
}
