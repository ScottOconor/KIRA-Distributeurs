package com.erp.sales.service;

import com.erp.common.entity.Company;
import com.erp.sales.entity.SalesOrder;
import com.erp.sales.entity.SalesOrderLine;
import com.erp.stock.entity.Product;
import com.erp.stock.entity.StockQuant;
import com.erp.stock.repository.ProductRepository;
import com.erp.stock.repository.StockQuantRepository;
import com.erp.stock.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * STK-01 : le stock disponible est vérifié par article (quantités cumulées), plus ligne par ligne.
 * Avant, deux lignes de 95 d'un article à 95 en stock étaient validées et le stock passait à 0
 * (survente).
 */
@ExtendWith(MockitoExtension.class)
class SalesStockAggregationTest {

    @Mock private ProductRepository stockProductRepo;
    @Mock private StockQuantRepository stockQuantRepo;
    @Mock private WarehouseRepository warehouseRepo;

    @InjectMocks
    private SalesService salesService;

    private SalesOrder order(BigDecimal... quantities) {
        Company company = new Company();
        company.setId(1L);
        SalesOrder order = SalesOrder.builder().company(company).build();
        for (BigDecimal q : quantities) {
            order.getLines().add(SalesOrderLine.builder().productId(10L).quantity(q).build());
        }
        return order;
    }

    private void stockOf(String qty) {
        Product product = Product.builder().id(10L).name("Bière 65cl").type("product").build();
        when(stockProductRepo.findById(10L)).thenReturn(Optional.of(product));
        StockQuant quant = StockQuant.builder().productId(10L).quantity(new BigDecimal(qty))
                .reservedQuantity(BigDecimal.ZERO).build();
        when(stockQuantRepo.findByProductIdAndCompanyId(10L, 1L)).thenReturn(List.of(quant));
    }

    private void validate(SalesOrder order) throws Throwable {
        Method m = SalesService.class.getDeclaredMethod("validateStockAvailability", SalesOrder.class);
        m.setAccessible(true);
        try {
            m.invoke(salesService, order);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void deuxLignesDuMemeArticleAuDelaDuStockSontRefusees() {
        stockOf("95");
        SalesOrder order = order(new BigDecimal("95"), new BigDecimal("95"));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> validate(order));

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stock insuffisant").hasMessageContaining("190");
    }

    @Test
    void deuxLignesDuMemeArticleDansLeStockSontAcceptees() {
        stockOf("95");
        SalesOrder order = order(new BigDecimal("45"), new BigDecimal("50"));

        assertThatCode(() -> validate(order)).doesNotThrowAnyException();
    }
}
