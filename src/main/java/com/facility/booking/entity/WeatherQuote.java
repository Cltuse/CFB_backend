package com.facility.booking.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_quote")
@Data
public class WeatherQuote {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "weather_type", nullable = false, length = 50)
    private String weatherType;
    
    @Column(name = "content", nullable = false, length = 500)
    private String content;
    
    @Column(name = "author", length = 100)
    private String author;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}