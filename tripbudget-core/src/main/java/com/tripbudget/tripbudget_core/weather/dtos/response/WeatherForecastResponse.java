package com.tripbudget.tripbudget_core.weather.dtos.response;

import java.time.Instant;
import java.util.List;

public record WeatherForecastResponse(String location, String timezone, Instant fetchedAt,
                                      List<WeatherDayResponse> days) {}
