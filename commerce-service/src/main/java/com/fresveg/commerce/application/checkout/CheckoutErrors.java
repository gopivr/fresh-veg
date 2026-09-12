package com.fresveg.commerce.application.checkout;
import com.fresveg.commerce.application.CommerceException;
import org.springframework.http.HttpStatus;
public final class CheckoutErrors {
 private CheckoutErrors() { }
 public static CommerceException conflict(String detail) {return new CommerceException(HttpStatus.CONFLICT,"CHK-409-001",detail);}
 public static CommerceException unavailable(String detail) {return new CommerceException(HttpStatus.SERVICE_UNAVAILABLE,"CHK-503-001",detail);}
 public static CommerceException missing() {return new CommerceException(HttpStatus.NOT_FOUND,"CHK-404-001","Owned resource not found.");}
}
