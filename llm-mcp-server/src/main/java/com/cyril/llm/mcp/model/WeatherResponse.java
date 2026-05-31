package com.cyril.llm.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WeatherResponse {
    private String city;
    private String date;
    private String weather;
    private double temperature;
}
