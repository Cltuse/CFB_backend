package com.facility.booking.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserFacilityBehaviorId implements Serializable {
    private Long userId;
    private Long facilityId;
    private String categoryName;
}
