package com.fresveg.catalog.infrastructure.persistence;
import com.fresveg.catalog.domain.UnitOfMeasure;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure,UUID> { Optional<UnitOfMeasure> findByCode(String code); }
