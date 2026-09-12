package com.fresveg.commerce.infrastructure.persistence;
import com.fresveg.commerce.domain.CartItem;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
public interface CartItemRepository extends JpaRepository<CartItem,UUID> {
 Optional<CartItem> findByIdAndCartId(UUID id,UUID cartId);
 long countByCartId(UUID cartId);
 @Query("select i from CartItem i where i.cartId=:cart and (:cursor is null or i.id>:cursor) order by i.id")
 List<CartItem> page(UUID cart,UUID cursor,Pageable pageable);
}
