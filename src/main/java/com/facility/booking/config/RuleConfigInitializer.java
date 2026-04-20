package com.facility.booking.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startup bootstrap for default rule config.
 * Temporarily disabled to avoid deployment-time side effects.
 */
@Component
public class RuleConfigInitializer implements CommandLineRunner {

    @Override
    public void run(String... args) {
        // Disabled on startup for deployment stability.
    }
}
