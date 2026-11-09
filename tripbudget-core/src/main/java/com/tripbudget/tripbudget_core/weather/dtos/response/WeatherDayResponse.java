package com.tripbudget.tripbudget_core.weather.dtos.response;

import com.tripbudget.tripbudget_core.weather.enums.WeatherCondition;
import java.time.LocalDate;

public record WeatherDayResponse(LocalDate date, double meanTempC, WeatherCondition condition) {}
