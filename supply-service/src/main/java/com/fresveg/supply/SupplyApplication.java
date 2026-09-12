package com.fresveg.supply;

import org.springframework.boot.SpringApplication;
import com.fresveg.common.cache.CacheConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CacheConfiguration.class)
public class SupplyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SupplyApplication.class, args);
    }
}
