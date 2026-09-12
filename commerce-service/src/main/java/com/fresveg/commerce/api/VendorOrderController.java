package com.fresveg.commerce.api;
import com.fresveg.commerce.api.dto.ApiResponse;
import com.fresveg.commerce.api.dto.OrderContracts.*;
import com.fresveg.commerce.application.order.VendorOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(value="/api/v1/vendor/orders",produces="application/json") @SecurityRequirement(name="bearerAuth")
public class VendorOrderController {
 private final VendorOrderService service;
 public VendorOrderController(VendorOrderService service) {this.service=service;}
 @GetMapping @Operation(operationId="listVendorOrders",summary="Read a verified vendor's orders with only its item snapshots")
 public ApiResponse<VendorOrderPage> list(@RequestParam UUID vendorId,@RequestParam(defaultValue="10") int pageSize,@RequestParam(required=false) UUID cursor,HttpServletRequest r) {return response(service.list(vendorId,pageSize,cursor),r);}
 @GetMapping("/{orderId}") @Operation(operationId="getVendorOrder",summary="Read only the verified vendor's items and order state")
 public ApiResponse<VendorOrderResponse> read(@RequestParam UUID vendorId,@PathVariable UUID orderId,HttpServletRequest r) {return response(service.read(vendorId,orderId),r);}
 @PostMapping(value="/{orderId}/accept",consumes="application/json") @Operation(operationId="acceptVendorOrder",summary="Vendor administrator accepts its items when the order is still awaiting payment")
 public ApiResponse<VendorOrderResponse> accept(@RequestParam UUID vendorId,@PathVariable UUID orderId,@Valid @RequestBody VendorDecisionRequest body,HttpServletRequest r) {return response(service.decide(vendorId,orderId,body,true),r);}
 @PostMapping(value="/{orderId}/reject",consumes="application/json") @Operation(operationId="rejectVendorOrder",summary="Vendor administrator rejects its items when the order is still awaiting payment")
 public ApiResponse<VendorOrderResponse> reject(@RequestParam UUID vendorId,@PathVariable UUID orderId,@Valid @RequestBody VendorDecisionRequest body,HttpServletRequest r) {return response(service.decide(vendorId,orderId,body,false),r);}
 private static <T> ApiResponse<T> response(T data,HttpServletRequest r) {return new ApiResponse<>(data,new ApiResponse.Meta(CommerceProblems.requestId(r),Instant.now()));}
}
