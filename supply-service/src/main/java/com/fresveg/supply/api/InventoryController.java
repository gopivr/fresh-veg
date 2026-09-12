package com.fresveg.supply.api;
import com.fresveg.supply.api.dto.*;
import com.fresveg.supply.api.dto.InventoryContracts.*;
import com.fresveg.supply.application.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(produces="application/json") @SecurityRequirement(name="bearerAuth")
public class InventoryController {
 private final InventoryService service;
 public InventoryController(InventoryService service) { this.service=service; }
 @GetMapping("/api/v1/supply/vendor/inventory") @Operation(operationId="listInventory",summary="List inventory for a verified vendor")
 public CollectionResponse<InventoryResponse> list(@RequestParam UUID vendorId,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String cursor,HttpServletRequest r) { return page(service.list(vendorId,pageSize,cursor),r); }
 @PostMapping(value="/api/v1/supply/vendor/inventory",consumes="application/json") @Operation(operationId="initializeInventory",summary="Initialize zero stock for an owned listing; repeated initialization returns existing inventory")
 public ApiResponse<InventoryResponse> initialize(@Valid @RequestBody InitializeInventoryRequest body,HttpServletRequest r) { return response(service.initialize(body),r); }
 @PutMapping(value="/api/v1/supply/vendor/inventory/{inventoryId}",consumes="application/json") @Operation(operationId="adjustInventory",summary="Apply a versioned batch correction with an immutable ledger entry")
 public ApiResponse<InventoryResponse> adjust(@PathVariable UUID inventoryId,@Valid @RequestBody AdjustInventoryRequest body,HttpServletRequest r) { return response(service.adjust(inventoryId,body),r); }
 @PostMapping(value="/api/v1/supply/vendor/inventory/{inventoryId}/adjustments",consumes="application/json") @Operation(operationId="recordInventoryAdjustment",summary="Record a signed batch correction using the current inventory version")
 public ApiResponse<InventoryResponse> adjustment(@PathVariable UUID inventoryId,@Valid @RequestBody AdjustInventoryRequest body,HttpServletRequest r) { return response(service.adjust(inventoryId,body),r); }
 @PostMapping(value="/api/v1/supply/vendor/inventory/{inventoryId}/batches",consumes="application/json") @Operation(operationId="receiveInventoryBatch",summary="Receive a new batch and record its stock movement atomically")
 public ApiResponse<InventoryResponse> receive(@PathVariable UUID inventoryId,@Valid @RequestBody ReceiveBatchRequest body,HttpServletRequest r) { return response(service.receive(inventoryId,body),r); }
 @GetMapping("/api/v1/supply/vendor/inventory/{inventoryId}/batches") @Operation(operationId="listInventoryBatches",summary="List owned batch metadata and quantities")
 public CollectionResponse<BatchResponse> batches(@PathVariable UUID inventoryId,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String cursor,HttpServletRequest r) { return page(service.batches(inventoryId,pageSize,cursor),r); }
 @GetMapping("/api/v1/supply/vendor/inventory/{inventoryId}/transactions") @Operation(operationId="listInventoryTransactions",summary="Read the append-only stock and reservation ledger")
 public CollectionResponse<TransactionResponse> transactions(@PathVariable UUID inventoryId,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String cursor,HttpServletRequest r) { return page(service.transactions(inventoryId,pageSize,cursor),r); }
 @PostMapping(value="/internal/v1/inventory/reservations",consumes="application/json") @Operation(operationId="reserveInventory",summary="Configured service reserves eligible stock with an idempotent external reference")
 public ApiResponse<ReservationResponse> reserve(@Valid @RequestBody ReserveInventoryRequest body,HttpServletRequest r) { return response(service.reserve(body),r); }
 @PostMapping("/internal/v1/inventory/reservations/{reservationId}/commit") @Operation(operationId="commitInventoryReservation",summary="Commit an owned active reservation once; expired reservations are released")
 public ApiResponse<ReservationResponse> commit(@PathVariable UUID reservationId,HttpServletRequest r) { return response(service.finish(reservationId,true),r); }
 @PostMapping("/internal/v1/inventory/reservations/{reservationId}/release") @Operation(operationId="releaseInventoryReservation",summary="Release an owned active reservation once")
 public ApiResponse<ReservationResponse> release(@PathVariable UUID reservationId,HttpServletRequest r) { return response(service.finish(reservationId,false),r); }
 @PostMapping(value="/internal/v1/inventory/order-reservations",consumes="application/json") @Operation(operationId="reserveOrderInventory",summary="Reserve a listing with batch eligibility through the delivery horizon")
 public ApiResponse<ReservationResponse> reserveOrder(@Valid @RequestBody OrderReservationRequest body,HttpServletRequest r) { return response(service.reserveOrder(body),r); }
 @GetMapping("/internal/v1/inventory/reservations/by-reference") @Operation(operationId="getInventoryReservationByReference",summary="Recover only the calling service's reservation by reference")
 public ApiResponse<ReservationResponse> reservation(@RequestParam @jakarta.validation.constraints.Size(min=1,max=160) String externalReference,HttpServletRequest r) { return response(service.reservation(externalReference),r); }
 private static ApiResponse.Meta meta(HttpServletRequest r) { return new ApiResponse.Meta(SupplyProblems.requestId(r),Instant.now()); }
 private static <T> ApiResponse<T> response(T data,HttpServletRequest r) { return new ApiResponse<>(data,meta(r)); }
 private static <T> CollectionResponse<T> page(SupplyService.Page<T> p,HttpServletRequest r) { return new CollectionResponse<>(p.data(),new CollectionResponse.Pagination(p.nextCursor(),p.hasNext()),meta(r)); }
}
