package com.facility.booking.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Weather {
    private String weatherType;
    private String temperature;
    private String weatherIcon;
    private String moodQuote;
    private String city;
    private String updateTime;
}
