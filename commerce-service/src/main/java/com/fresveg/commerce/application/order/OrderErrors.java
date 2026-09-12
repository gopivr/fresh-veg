package com.fresveg.commerce.application.order;
import com.fresveg.commerce.application.CommerceException;
import org.springframework.http.HttpStatus;
public final class OrderErrors {
 private OrderErrors() { }
 public static CommerceException conflict(String message) { return new CommerceException(HttpStatus.CONFLICT,"ORD-409-001",message); }
 public static CommerceException missing() { return new CommerceException(HttpStatus.NOT_FOUND,"ORD-404-001","Owned order not found."); }
 public static CommerceException invalid(String message) { return new CommerceException(HttpStatus.BAD_REQUEST,"ORD-400-001",message); }
}
