package com.tripbudget.tripbudget_core.weather.enums;

public enum WeatherCondition {
    UNKNOWN, CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDERSTORM;

    public static WeatherCondition fromWmo(Integer code) {
        if (code == null) return UNKNOWN;
        return switch (code) {
            case 0, 1 -> CLEAR;
            case 2 -> PARTLY_CLOUDY;
            case 3 -> CLOUDY;
            case 45, 48 -> FOG;
            case 51, 53, 55, 56, 57 -> DRIZZLE;
            case 61, 63, 65, 66, 67, 80, 81, 82 -> RAIN;
            case 71, 73, 75, 77, 85, 86 -> SNOW;
            case 95, 96, 97, 99 -> THUNDERSTORM;
            default -> UNKNOWN;
        };
    }

    public WeatherCondition moreDisruptiveThan(WeatherCondition other) {
        return ordinal() > other.ordinal() ? this : other;
    }
}
