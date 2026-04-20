package com.facility.booking.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Forecast {
    private String date;
    private String high;
    private String low;
    private String type;
    private String fengxiang;
    private String fengli;
}