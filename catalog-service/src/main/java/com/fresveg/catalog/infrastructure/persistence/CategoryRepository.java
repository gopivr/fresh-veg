package com.fresveg.catalog.infrastructure.persistence;
import com.fresveg.catalog.domain.Category;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category,UUID> { long countByIdIn(java.util.Collection<UUID> ids); List<Category> findByIdGreaterThanOrderByIdAsc(UUID id, org.springframework.data.domain.Pageable limit); List<Category> findAllByOrderByIdAsc(org.springframework.data.domain.Pageable limit); }
