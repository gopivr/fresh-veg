package com.fresveg.fulfillment.api;
import com.fresveg.fulfillment.api.dto.*;
import com.fresveg.fulfillment.api.dto.FulfillmentContracts.*;
import com.fresveg.fulfillment.application.FulfillmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(produces="application/json") @SecurityRequirement(name="bearerAuth")
public class FulfillmentController {
 private final FulfillmentService service;
 public FulfillmentController(FulfillmentService service){this.service=service;}
 @GetMapping("/api/v1/fulfillment/slots") @Operation(operationId="listDeliverySlots",summary="List open delivery slots by service area and time window")
 public ApiResponse<List<DeliverySlotResponse>> slots(@RequestParam(required=false) String serviceArea,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest r){return response(service.slots(serviceArea,from,to,pageSize),r);}
 @GetMapping("/api/v1/fulfillments/{id}") @Operation(operationId="getFulfillment",summary="Read fulfillment, items and shipment state")
 public ApiResponse<FulfillmentResponse> read(@PathVariable UUID id,HttpServletRequest r){return response(service.read(id),r);}
 @GetMapping("/api/v1/fulfillments/{id}/tracking") @Operation(operationId="getFulfillmentTracking",summary="Read shipment tracking events for a fulfillment")
 public ApiResponse<TrackingResponse> tracking(@PathVariable UUID id,HttpServletRequest r){return response(service.tracking(id),r);}
 @PostMapping(value="/internal/v1/fulfillments",consumes="application/json") @Operation(operationId="createFulfillment",summary="Configured service creates a fulfillment for a confirmed order")
 public ApiResponse<FulfillmentResponse> create(@Valid @RequestBody CreateFulfillmentRequest body,HttpServletRequest r){return response(service.create(body),r);}
 @PostMapping(value="/internal/v1/fulfillments/{id}/transitions",consumes="application/json") @Operation(operationId="transitionFulfillment",summary="Configured service advances fulfillment state")
 public ApiResponse<FulfillmentResponse> transition(@PathVariable UUID id,@Valid @RequestBody TransitionRequest body,HttpServletRequest r){return response(service.transition(id,body),r);}
 @PostMapping(value="/internal/v1/fulfillment/slots",consumes="application/json") @Operation(operationId="createDeliverySlot",summary="Configured service creates an operational delivery slot")
 public ApiResponse<DeliverySlotResponse> seed(@Valid @RequestBody DeliverySlotSeedRequest body,HttpServletRequest r){return response(service.seed(body),r);}
 private static <T> ApiResponse<T> response(T data,HttpServletRequest r){return new ApiResponse<>(data,new ApiResponse.Meta(FulfillmentProblems.requestId(r),Instant.now()));}
}
