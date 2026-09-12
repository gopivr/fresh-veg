package com.fresveg.catalog.application;

import com.fresveg.catalog.api.dto.*;
import com.fresveg.catalog.domain.*;
import com.fresveg.catalog.infrastructure.persistence.*;
import com.fresveg.catalog.infrastructure.security.CatalogAuthorization;
import com.fresveg.common.cache.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class CatalogService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final ProductCategoryRepository relations;
    private final UnitOfMeasureRepository units;
    private final ProductSearchRepository search;
    private final CatalogAuthorization authorization;
    private final CatalogMapper mapper;
    private final CatalogCursor cursors;
    private final CatalogJson json;
    private final TtlCache cache;
    private final Duration cacheTtl;
    public CatalogService(ProductRepository products,CategoryRepository categories,ProductVariantRepository variants,
            ProductImageRepository images,ProductCategoryRepository relations,UnitOfMeasureRepository units,
            ProductSearchRepository search,CatalogAuthorization authorization,CatalogMapper mapper,CatalogCursor cursors,CatalogJson json,
            TtlCache cache,CacheProperties cacheProperties) {
        this.products=products;this.categories=categories;this.variants=variants;this.images=images;this.relations=relations;
        this.units=units;this.search=search;this.authorization=authorization;this.mapper=mapper;this.cursors=cursors;this.json=json;
        this.cache=cache;this.cacheTtl=cacheProperties.getTtl();
    }
    public Page<ProductSummary> products(CatalogQuery query) {
        pageSize(query.pageSize());
        if (!Set.of("name","code").contains(query.sort())) { throw invalid("sort must be name or code."); }
        String q=query.q()==null || query.q().isBlank()?null:query.q().strip();
        if (q!=null && q.length()>120) { throw invalid("q must be at most 120 characters."); }
        ProductStatus status=query.status()==null?ProductStatus.ACTIVE:query.status();
        if (status!=ProductStatus.ACTIVE) { authorization.requireAdmin(); }
        if (query.categoryId()!=null && !categories.existsById(query.categoryId())) { throw missing("Category"); }
        var normalized=new CatalogQuery(q,query.categoryId(),query.organic(),status,query.sort(),query.pageSize(),query.cursor(),json.filter(query.attributes()));
        String filter=cursors.fingerprint(Arrays.asList("products",q,query.categoryId(),query.organic(),status,query.sort(),normalized.attributes()));
        String key="catalog:products:"+filter+":"+query.pageSize()+":"+Objects.toString(query.cursor(),"");
        return cache.get(key,cacheTtl,() -> {
            var rows=search.search(normalized,cursors.decode(query.cursor(),filter));
            return page(rows,query.pageSize(),filter,mapper::summary,p -> "code".equals(query.sort())?p.getCode():p.getName(),Product::getId);
        });
    }
    public ProductResponse product(UUID id) {
        var product=products.findById(id).orElseThrow(() -> missing("Product"));
        boolean privileged=product.getStatus()!=ProductStatus.ACTIVE;
        if (privileged && !authorization.isAdmin()) { throw missing("Product"); }
        return detail(product,privileged);
    }
    public Page<CategoryResponse> categories(int pageSize,String cursor) {
        pageSize(pageSize);
        String filter=cursors.fingerprint("categories");
        var position=cursors.decode(cursor,filter);
        var limit=PageRequest.ofSize(pageSize+1);
        String key="catalog:categories:"+pageSize+":"+Objects.toString(cursor,"");
        return cache.get(key,cacheTtl,() -> {
            var rows=position==null?categories.findAllByOrderByIdAsc(limit):categories.findByIdGreaterThanOrderByIdAsc(position.id(),limit);
            return page(rows,pageSize,filter,mapper::category,c -> "",Category::getId);
        });
    }
    @Transactional
    @PreAuthorize("@catalogAuthorization.isAdmin()")
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        UUID actor=authorization.requireAdmin();
        if (request.parentCategoryId()!=null && !categories.existsById(request.parentCategoryId())) { throw missing("Parent category"); }
        // IDs are server-generated, parents must already exist, and this phase has no reparent operation: cycles cannot be introduced.
        var result=mapper.category(categories.saveAndFlush(new Category(request.code(),request.name(),request.parentCategoryId(),actor)));
        cache.evictByPrefix("catalog:");
        return result;
    }
    @Transactional
    @PreAuthorize("@catalogAuthorization.isAdmin()")
    public ProductResponse create(CreateProductRequest request) {
        UUID actor=authorization.requireAdmin(); validate(request);
        var product=new Product(actor); replace(product,request,actor);
        products.saveAndFlush(product); replaceChildren(product,request,actor);
        cache.evictByPrefix("catalog:");
        return detail(product,true);
    }
    @Transactional
    @PreAuthorize("@catalogAuthorization.isAdmin()")
    public ProductResponse replace(UUID id,UpdateProductRequest request) {
        UUID actor=authorization.requireAdmin(); validate(request);
        var product=products.findById(id).orElseThrow(() -> missing("Product"));
        version(product,request.version()); replace(product,request,actor);
        // Lock the aggregate version before changing its children; competing writers roll back atomically.
        products.saveAndFlush(product); replaceChildren(product,request,actor);
        cache.evictByPrefix("catalog:");
        return detail(product,true);
    }
    @Transactional
    @PreAuthorize("@catalogAuthorization.isAdmin()")
    public ProductResponse patch(UUID id,PatchProductRequest request) {
        UUID actor=authorization.requireAdmin();
        var product=products.findById(id).orElseThrow(() -> missing("Product"));
        version(product,request.version()); product.changeStatus(request.status(),actor);
        var result=detail(products.saveAndFlush(product),true);
        cache.evictByPrefix("catalog:");
        return result;
    }
    private void validate(ProductInput request) {
        json.attributes(request.attributes());
        if (new HashSet<>(request.categoryIds()).size()!=request.categoryIds().size()) { throw invalid("Duplicate categories are not allowed."); }
        if (categories.countByIdIn(request.categoryIds())!=request.categoryIds().size()) { throw invalid("Every category must exist."); }
        if (request.variants().stream().map(VariantRequest::code).distinct().count()!=request.variants().size()) { throw invalid("Variant codes must be unique."); }
        for (var variant:request.variants()) { unit(variant.unitCode()); }
    }
    private void replace(Product p,ProductInput r,UUID actor) { p.replace(r.code(),r.name(),r.description(),r.organic(),r.status(),r.attributes(),actor); }
    private void replaceChildren(Product p,ProductInput request,UUID actor) {
        var existing=variants.findByProductIdOrderByCodeAsc(p.getId()).stream().collect(Collectors.toMap(ProductVariant::getCode,Function.identity()));
        for (var v:request.variants()) {
            var row=existing.remove(v.code());
            if (row==null) { row=new ProductVariant(p.getId(),v.code(),actor); }
            row.replace(v.name(),unit(v.unitCode()).getId(),v.quantity(),v.status(),actor); variants.save(row);
        }
        existing.values().forEach(v -> v.archive(actor));
        relations.removeForProduct(p.getId());
        for (UUID category:request.categoryIds()) { relations.save(new ProductCategory(p.getId(),category,actor)); }
        images.removeForProduct(p.getId());
        int position=0;
        for (var image:request.images()) { images.save(new ProductImage(p.getId(),image.url(),image.altText(),position++,actor)); }
        products.flush();
    }
    private UnitOfMeasure unit(String code) { return units.findByCode(code).orElseThrow(() -> invalid("Unknown unitCode; supported units are KG, G, EA, L and ML.")); }
    private ProductResponse detail(Product p,boolean includeArchived) {
        var unitCodes=units.findAll().stream().collect(Collectors.toMap(UnitOfMeasure::getId,UnitOfMeasure::getCode));
        var rows=variants.findByProductIdOrderByCodeAsc(p.getId()).stream().filter(v -> includeArchived || v.getStatus()==VariantStatus.ACTIVE).toList();
        return mapper.product(p,relations.findByProductIdOrderByCategoryIdAsc(p.getId()).stream().map(ProductCategory::getCategoryId).toList(),
                rows,unitCodes,images.findByProductIdOrderByPositionAsc(p.getId()));
    }
    private static void version(Product p,long version) {
        if (p.getVersion()!=version) { throw new CatalogException(HttpStatus.CONFLICT,"CAT-409-001","The product has changed; reload it before updating."); }
    }
    private static void pageSize(int size) { if (size<1 || size>100) { throw invalid("pageSize must be between 1 and 100."); } }
    private static CatalogException invalid(String message) { return new CatalogException(HttpStatus.BAD_REQUEST,"CAT-400-001",message); }
    private static CatalogException missing(String resource) { return new CatalogException(HttpStatus.NOT_FOUND,"CAT-404-001",resource+" not found."); }
    private <E,D> Page<D> page(List<E> rows,int size,String filter,Function<E,D> mapper,Function<E,String> value,Function<E,UUID> id) {
        boolean more=rows.size()>size; var visible=rows.subList(0,Math.min(rows.size(),size));
        String next=more?cursors.encode(filter,value.apply(visible.getLast()),id.apply(visible.getLast())):null;
        return new Page<>(visible.stream().map(mapper).toList(),next,more);
    }
    public record Page<T>(List<T> data,String nextCursor,boolean hasNext) { }
}
