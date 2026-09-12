package com.fresveg.fulfillment;

import org.springframework.boot.SpringApplication;
import com.fresveg.common.cache.CacheConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CacheConfiguration.class)
public class FulfillmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(FulfillmentApplication.class, args);
    }
}
