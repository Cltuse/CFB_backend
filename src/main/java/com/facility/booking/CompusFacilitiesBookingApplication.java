package com.facility.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class CompusFacilitiesBookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompusFacilitiesBookingApplication.class, args);
    }
}
