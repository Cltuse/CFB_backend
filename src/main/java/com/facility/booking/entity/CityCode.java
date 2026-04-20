package com.facility.booking.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "citycode")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CityCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "code", nullable = false, length = 20)
    private String code;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}