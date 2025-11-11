package de.seuhd.campuscoffee.data.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration class for WebClient beans used in external API calls.
 * <p>
 * This configuration provides a pre-configured WebClient instance for calling
 * the OpenStreetMap API with appropriate base URL and required headers.
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates a WebClient bean configured for OpenStreetMap API access.
     * <p>
     * The OSM API requires a User-Agent header for all requests. This WebClient
     * is pre-configured with:
     * <ul>
     *   <li>Base URL: https://api.openstreetmap.org/api/0.6</li>
     *   <li>User-Agent header: CampusCoffee/1.0 (as required by OSM API policy)</li>
     * </ul>
     *
     * @return Configured WebClient instance for OSM API calls
     */
    @Bean
    public WebClient osmWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.openstreetmap.org/api/0.6")
                .defaultHeader("User-Agent", "CampusCoffee/1.0")
                .build();
    }
}
