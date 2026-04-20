package com.facility.booking.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Bean
    public ObjectMapper objectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        
        // Custom deserializer that can handle multiple formats
        javaTimeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String dateStr = p.getText();
                if (dateStr == null || dateStr.trim().isEmpty()) {
                    return null;
                }
                
                try {
                    // Try ISO format first (with or without Z)
                    if (dateStr.contains("T")) {
                        String cleanedDate = dateStr.replace("Z", "");
                        return LocalDateTime.parse(cleanedDate, ISO_DATE_TIME_FORMATTER);
                    }
                    
                    // Try standard format
                    return LocalDateTime.parse(dateStr, DATE_TIME_FORMATTER);
                } catch (DateTimeParseException e) {
                    // If both formats fail, return current time as fallback
                    return LocalDateTime.now();
                }
            }
        });
        
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));

        return Jackson2ObjectMapperBuilder.json()
                .modules(javaTimeModule)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}