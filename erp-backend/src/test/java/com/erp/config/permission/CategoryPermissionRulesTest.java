package com.erp.config.permission;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Les routes de création de catégorie doivent avoir une règle (sinon PermissionFilter, fail-closed, renvoie 403). */
class CategoryPermissionRulesTest {

    private final PermissionService service = new PermissionService();

    @Test
    void stockCategoryCreationRequiresProduitsCreate() {
        PermissionRule rule = service.findRule("POST", "/api/stock/categories");

        assertThat(rule).isNotNull();
        assertThat(rule.module() + "." + rule.resource() + "." + rule.action()).isEqualTo("STOCK.PRODUITS.CREATE");
    }

    @Test
    void userWithPermissionIsAllowedAndUserWithoutIsDenied() {
        PermissionRule rule = service.findRule("POST", "/api/stock/categories");

        var allowed = new UsernamePasswordAuthenticationToken("u", null,
                List.of(new SimpleGrantedAuthority("PERM_STOCK_PRODUITS_CREATE")));
        var denied = new UsernamePasswordAuthenticationToken("u", null,
                List.of(new SimpleGrantedAuthority("PERM_STOCK_PRODUITS_VIEW")));

        assertThat(service.hasPermission(allowed, rule)).isTrue();
        assertThat(service.hasPermission(denied, rule)).isFalse();
    }

    @Test
    void superAdminIsPrivileged() {
        var admin = new UsernamePasswordAuthenticationToken("root", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        assertThat(service.isPrivileged(admin)).isTrue();
    }
}
