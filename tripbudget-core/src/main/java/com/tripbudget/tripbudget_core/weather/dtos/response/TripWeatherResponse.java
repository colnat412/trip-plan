package com.tripbudget.tripbudget_core.weather.dtos.response;

import java.time.LocalDate;

public record TripWeatherResponse(String state, LocalDate date, WeatherForecastResponse forecast,
                                  WeatherSummaryResponse summary, String reason) {
    public TripWeatherResponse(String state, LocalDate date, WeatherForecastResponse forecast,
                               WeatherSummaryResponse summary) {
        this(state, date, forecast, summary, null);
    }
}
