package com.tripbudget.tripbudget_core.weather.dtos.response;

import com.tripbudget.tripbudget_core.weather.enums.WeatherCondition;
import java.time.LocalDate;

public record WeatherSummaryResponse(LocalDate startDate, LocalDate endDate, int dayCount,
                                     double temperatureC, WeatherCondition condition) {}
