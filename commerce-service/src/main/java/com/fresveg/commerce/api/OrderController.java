package com.fresveg.commerce.api;
import com.fresveg.commerce.api.dto.ApiResponse;
import com.fresveg.commerce.api.dto.OrderContracts.*;
import com.fresveg.commerce.application.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(value="/api/v1/orders",produces="application/json") @SecurityRequirement(name="bearerAuth")
public class OrderController {
 private final OrderService service;
 public OrderController(OrderService service) {this.service=service;}
 @PostMapping(consumes="application/json") @Operation(operationId="createOrder",summary="Create an order with authoritative snapshots, local payment authorization and committed inventory; replay a customer-scoped Idempotency-Key")
 public ResponseEntity<ApiResponse<OrderResponse>> create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateOrderRequest body,HttpServletRequest r) {var order=service.create(key,body);return ResponseEntity.created(URI.create("/api/v1/orders/"+order.orderId())).body(response(order,r));}
 @GetMapping("/{orderId}") @Operation(operationId="getOrder",summary="Read owned immutable snapshots and current lifecycle state")
 public ApiResponse<OrderResponse> read(@PathVariable UUID orderId,HttpServletRequest r) {return response(service.read(orderId),r);}
 @GetMapping @Operation(operationId="listOrders",summary="Read a cursor page of owned orders")
 public ApiResponse<OrderPage> list(@RequestParam(defaultValue="10") int pageSize,@RequestParam(required=false) UUID cursor,HttpServletRequest r) {return response(service.list(pageSize,cursor),r);}
 @PostMapping("/{orderId}/cancel") @Operation(operationId="cancelOrder",summary="Cancel an unpaid order after stock release or a confirmed order after local refund")
 public ApiResponse<OrderResponse> cancel(@PathVariable UUID orderId,HttpServletRequest r) {return response(service.cancel(orderId),r);}
 private static <T> ApiResponse<T> response(T data,HttpServletRequest r) {return new ApiResponse<>(data,new ApiResponse.Meta(CommerceProblems.requestId(r),Instant.now()));}
}
