package com.facility.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 用户设施行为视图实体
 */
@Data
@Entity
@Immutable
@IdClass(UserFacilityBehaviorId.class)
@Table(name = "user_facility_behavior")
public class UserFacilityBehavior {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "facility_id")
    private Long facilityId;

    @Id
    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "booking_count")
    private Integer bookingCount;

    @Column(name = "last_booking_time")
    private LocalDateTime lastBookingTime;
}
