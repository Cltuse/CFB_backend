package com.facility.booking.config;

import com.facility.booking.util.FileStoragePathUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:files}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = FileStoragePathUtils.resolveUploadRoot(uploadDir);
        String resourceLocation = uploadRoot.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }

        registry.addResourceHandler("/files/**")
                .addResourceLocations(resourceLocation);
    }
}
