package com.fresveg.catalog.api;

import com.fresveg.catalog.api.dto.*;
import com.fresveg.catalog.application.*;
import com.fresveg.catalog.domain.ProductStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/v1/catalog",produces="application/json")
@Tag(name="Catalog")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog=catalog; }
    @GetMapping("/products")
    @Operation(operationId="listProducts",summary="Browse active products; non-active status requires a stored platform administrator role")
    public CollectionResponse<ProductSummary> products(@RequestParam(required=false) String q,
            @RequestParam(required=false) UUID categoryId,@RequestParam(required=false) Boolean organic,
            @RequestParam(required=false) ProductStatus status,@RequestParam(defaultValue="name") String sort,
            @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String cursor,
            @RequestParam(required=false) String attributes,HttpServletRequest request) {
        return collection(catalog.products(new CatalogQuery(q,categoryId,organic,status,sort,pageSize,cursor,attributes)),request);
    }
    @GetMapping("/products/{productId}")
    @Operation(operationId="getProduct",summary="Read active product metadata; non-active products are visible only to administrators")
    public ApiResponse<ProductResponse> product(@PathVariable UUID productId,HttpServletRequest request) {
        return response(catalog.product(productId),request);
    }
    @GetMapping("/categories")
    @Operation(operationId="listCategories",summary="List categories with parent identifiers")
    public CollectionResponse<CategoryResponse> categories(@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String cursor,HttpServletRequest request) {
        return collection(catalog.categories(pageSize,cursor),request);
    }
    @GetMapping("/categories/{categoryId}/products")
    @Operation(operationId="listCategoryProducts",summary="Browse products directly assigned to a category")
    public CollectionResponse<ProductSummary> categoryProducts(@PathVariable UUID categoryId,@RequestParam(required=false) String q,
            @RequestParam(required=false) Boolean organic,@RequestParam(required=false) ProductStatus status,
            @RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String cursor,@RequestParam(required=false) String attributes,HttpServletRequest request) {
        return collection(catalog.products(new CatalogQuery(q,categoryId,organic,status,sort,pageSize,cursor,attributes)),request);
    }
    @PostMapping(value="/products",consumes="application/json")
    @Operation(operationId="createProduct",summary="Create a master product with variants, categories and images")
    @SecurityRequirement(name="bearerAuth")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest body,HttpServletRequest request) {
        var product=catalog.create(body);
        return ResponseEntity.created(URI.create("/api/v1/catalog/products/"+product.productId())).body(response(product,request));
    }
    @PutMapping(value="/products/{productId}",consumes="application/json")
    @Operation(operationId="replaceProduct",summary="Replace product metadata and children using the current aggregate version")
    @SecurityRequirement(name="bearerAuth")
    public ApiResponse<ProductResponse> replace(@PathVariable UUID productId,@Valid @RequestBody UpdateProductRequest body,HttpServletRequest request) {
        return response(catalog.replace(productId,body),request);
    }
    @PatchMapping(value="/products/{productId}",consumes="application/json")
    @Operation(operationId="patchProduct",summary="Change product status using the current version; other partial edits are not supported")
    @SecurityRequirement(name="bearerAuth")
    public ApiResponse<ProductResponse> patch(@PathVariable UUID productId,@Valid @RequestBody PatchProductRequest body,HttpServletRequest request) {
        return response(catalog.patch(productId,body),request);
    }
    @PostMapping(value="/categories",consumes="application/json")
    @Operation(operationId="createCategory",summary="Create a category beneath an existing optional parent")
    @SecurityRequirement(name="bearerAuth")
    public ResponseEntity<ApiResponse<CategoryResponse>> category(@Valid @RequestBody CreateCategoryRequest body,HttpServletRequest request) {
        var category=catalog.createCategory(body);
        return ResponseEntity.created(URI.create("/api/v1/catalog/categories/"+category.categoryId())).body(response(category,request));
    }
    private static <T> ApiResponse<T> response(T data,HttpServletRequest request) { return new ApiResponse<>(data,meta(request)); }
    private static <T> CollectionResponse<T> collection(CatalogService.Page<T> page,HttpServletRequest request) {
        return new CollectionResponse<>(page.data(),new CollectionResponse.Pagination(page.nextCursor(),page.hasNext()),meta(request));
    }
    private static ApiResponse.Meta meta(HttpServletRequest request) { return new ApiResponse.Meta(CatalogProblems.requestId(request),Instant.now()); }
}
