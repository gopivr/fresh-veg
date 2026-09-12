package com.fresveg.catalog.infrastructure.persistence;

import com.fresveg.catalog.application.*;
import com.fresveg.catalog.domain.Product;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ProductSearchRepository {
    private final EntityManager entities;
    public ProductSearchRepository(EntityManager entities) { this.entities=entities; }
    @SuppressWarnings("unchecked")
    public List<Product> search(CatalogQuery request, CatalogCursor.Position cursor) {
        // Sort SQL is selected from a closed vocabulary, never concatenated from request text.
        String column="code".equals(request.sort()) ? "code" : "name";
        StringBuilder sql=new StringBuilder("SELECT p.* FROM catalog.products p WHERE p.status=:status");
        Map<String,Object> parameters=new LinkedHashMap<>(); parameters.put("status",request.status().name());
        if (request.q()!=null) {
            sql.append(" AND to_tsvector('simple',p.name) @@ websearch_to_tsquery('simple',:q)"); parameters.put("q",request.q());
        }
        if (request.organic()!=null) { sql.append(" AND p.organic=:organic"); parameters.put("organic",request.organic()); }
        if (request.categoryId()!=null) {
            sql.append(" AND EXISTS (SELECT 1 FROM catalog.product_categories c WHERE c.product_id=p.product_id AND c.category_id=:category)");
            parameters.put("category",request.categoryId());
        }
        if (request.attributes()!=null) {
            sql.append(" AND p.attributes @> CAST(:attributes AS jsonb)"); parameters.put("attributes",request.attributes());
        }
        if (cursor!=null) {
            sql.append(" AND (p.").append(column).append(",p.product_id) > (:value,:id)");
            parameters.put("value",cursor.value()); parameters.put("id",cursor.id());
        }
        sql.append(" ORDER BY p.").append(column).append(",p.product_id");
        var query=entities.createNativeQuery(sql.toString(),Product.class).setMaxResults(request.pageSize()+1);
        parameters.forEach(query::setParameter);
        return query.getResultList();
    }
}
