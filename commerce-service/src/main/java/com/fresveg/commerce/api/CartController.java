package com.fresveg.commerce.api;
import com.fresveg.commerce.api.dto.ApiResponse;
import com.fresveg.commerce.api.dto.CartContracts.*;
import com.fresveg.commerce.application.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(value="/api/v1/carts",produces="application/json") @SecurityRequirement(name="bearerAuth")
public class CartController {
 private final CartService service;
 public CartController(CartService service) { this.service=service; }
 @PostMapping(consumes="application/json") @Operation(operationId="createCart",summary="Create a cart owned by the authenticated Account customer")
 public ResponseEntity<ApiResponse<CartResponse>> create(@Valid @RequestBody CreateCartRequest body,HttpServletRequest r) { var cart=service.create(body);return ResponseEntity.created(URI.create("/api/v1/carts/"+cart.cartId())).body(response(cart,r)); }
 @GetMapping("/{cartId}") @Operation(operationId="getCart",summary="Read a page of cart items with current Supply prices")
 public ApiResponse<CartResponse> read(@PathVariable UUID cartId,@RequestParam(defaultValue="10") int pageSize,@RequestParam(required=false) String cursor,HttpServletRequest r) { return response(service.read(cartId,pageSize,cursor),r); }
 @PostMapping(value="/{cartId}/items",consumes="application/json") @Operation(operationId="addCartItem",summary="Add a validated listing and quantity using the current cart version")
 public ApiResponse<MutationResponse> add(@PathVariable UUID cartId,@Valid @RequestBody AddCartItemRequest body,HttpServletRequest r) { return response(service.add(cartId,body),r); }
 @PatchMapping(value="/{cartId}/items/{itemId}",consumes="application/json") @Operation(operationId="updateCartItem",summary="Replace quantity using the current cart version")
 public ApiResponse<MutationResponse> update(@PathVariable UUID cartId,@PathVariable UUID itemId,@Valid @RequestBody UpdateCartItemRequest body,HttpServletRequest r) { return response(service.update(cartId,itemId,body),r); }
 @DeleteMapping("/{cartId}/items/{itemId}") @Operation(operationId="removeCartItem",summary="Remove an owned item without requiring Supply availability")
 public ApiResponse<MutationResponse> remove(@PathVariable UUID cartId,@PathVariable UUID itemId,@RequestParam @Min(0) long version,HttpServletRequest r) { return response(service.remove(cartId,itemId,version),r); }
 private static <T> ApiResponse<T> response(T value,HttpServletRequest r) { return new ApiResponse<>(value,new ApiResponse.Meta(CommerceProblems.requestId(r),Instant.now())); }
}
