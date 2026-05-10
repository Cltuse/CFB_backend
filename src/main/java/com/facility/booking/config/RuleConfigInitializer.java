package com.facility.booking.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 初始化默认规则配置
 * 临时禁用，避免部署时 side effects
 */
@Component
public class RuleConfigInitializer implements CommandLineRunner {

    // 初始化默认规则配置
    // 临时禁用，避免部署时 side effects
    @Override
    public void run(String... args) {
        // Disabled on startup for deployment stability.
    }
}
