package com.fresveg.commerce.infrastructure.persistence;
import com.fresveg.commerce.domain.Cart;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface CartRepository extends JpaRepository<Cart,UUID> {
 Optional<Cart> findByIdAndCustomerId(UUID id,UUID customerId);
}
