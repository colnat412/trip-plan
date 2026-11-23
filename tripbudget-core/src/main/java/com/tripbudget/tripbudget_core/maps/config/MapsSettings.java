package com.tripbudget.tripbudget_core.maps.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class MapsSettings {
    private final String apiKey;
    private final int maxPoints;
    private final boolean requireHttps;
    private final boolean trustProxy;

    public MapsSettings(
            @Value("${maps.vietmap.api-key:}") String apiKey,
            @Value("${maps.vietmap.max-points:10}") int maxPoints,
            @Value("${maps.require-https:false}") boolean requireHttps,
            @Value("${maps.trust-proxy:false}") boolean trustProxy) {
        this.apiKey = apiKey.trim();
        this.maxPoints = maxPoints >= 2 && maxPoints <= 50 ? maxPoints : 10;
        this.requireHttps = requireHttps;
        this.trustProxy = trustProxy;
    }

}
