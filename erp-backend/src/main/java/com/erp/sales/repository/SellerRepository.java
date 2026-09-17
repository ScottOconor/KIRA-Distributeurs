package com.erp.sales.repository;

import com.erp.sales.entity.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {

    List<Seller> findByCompanyId(Long companyId);
    List<Seller> findByCompanyIdAndActiveTrue(Long companyId);
    Optional<Seller> findFirstByNameIgnoreCaseAndCompanyId(String name, Long companyId);
}
