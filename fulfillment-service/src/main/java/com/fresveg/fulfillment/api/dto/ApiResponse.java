package com.fresveg.fulfillment.api.dto;
import java.time.Instant;
public record ApiResponse<T>(T data,Meta meta) { public record Meta(String requestId,Instant timestamp) { } }
