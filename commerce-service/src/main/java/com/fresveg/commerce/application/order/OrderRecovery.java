package com.fresveg.commerce.application.order;
import com.fresveg.commerce.infrastructure.persistence.OrderRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
@Configuration(proxyBeanMethods=false) @EnableScheduling
public class OrderRecovery {
 private final OrderRepository orders;private final OrderService service;
 public OrderRecovery(OrderRepository orders,OrderService service) {this.orders=orders;this.service=service;}
 @Scheduled(fixedDelayString="${commerce.orders.recovery-delay-ms:30000}",initialDelayString="${commerce.orders.recovery-delay-ms:30000}")
 public void recover() {
  try {
   for(var id:orders.recovery())try{service.recoverIntent(id);}catch(RuntimeException e){failed(e);}
   for(var id:orders.expired())try{service.finishCancellation(id);}catch(RuntimeException e){failed(e);}
  } catch(RuntimeException e){failed(e);}
 }
 private static void failed(Exception e) {org.slf4j.LoggerFactory.getLogger(OrderRecovery.class).warn("Order recovery deferred ({})",e.getClass().getSimpleName());}
}
