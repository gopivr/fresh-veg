package com.fresveg.catalog.infrastructure.persistence;
import com.fresveg.catalog.domain.Product;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product,UUID> {  }
