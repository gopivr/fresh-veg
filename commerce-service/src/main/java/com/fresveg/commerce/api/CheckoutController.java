package com.fresveg.commerce.api;
import com.fresveg.commerce.api.dto.*;
import com.fresveg.commerce.api.dto.CheckoutContracts.*;
import com.fresveg.commerce.application.checkout.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;
@RestController
public class CheckoutController {
 private final CheckoutService service;
 public CheckoutController(CheckoutService service) {this.service=service;}
 @PostMapping(value="/api/v1/checkout/preview",consumes="application/json",produces="application/json")
 @Operation(operationId="previewCheckout",summary="Resolve owned address, current prices, eligible stock and delivery policy into a nonbinding checkout breakdown")
 @SecurityRequirement(name="bearerAuth")
 public ApiResponse<PreviewResponse> preview(@Valid @RequestBody PreviewRequest body,HttpServletRequest r) {return new ApiResponse<>(service.preview(body),new ApiResponse.Meta(CommerceProblems.requestId(r),Instant.now()));}
}
