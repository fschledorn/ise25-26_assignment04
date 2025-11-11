package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OsmDataServiceImpl.
 * Tests the HTTP client logic for fetching OSM nodes, including success and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
class OsmDataServiceImplTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private OsmDataServiceImpl osmDataService;

    @BeforeEach
    void setUp() {
        osmDataService = new OsmDataServiceImpl(webClient);
    }

    @Test
    void fetchNode_shouldReturnOsmNodeWithTags_whenNodeExists() {
        // Arrange: Mock successful API response
        Long nodeId = 5589879349L;
        String jsonResponse = """
                {
                  "version": "0.6",
                  "generator": "CGImap 0.9.5",
                  "elements": [
                    {
                      "type": "node",
                      "id": 5589879349,
                      "lat": 49.4130716,
                      "lon": 8.6911353,
                      "tags": {
                        "addr:city": "Heidelberg",
                        "addr:housenumber": "21",
                        "addr:postcode": "69117",
                        "addr:street": "Untere Straße",
                        "amenity": "cafe",
                        "cuisine": "coffee_shop",
                        "name": "Rada Coffee & Rösterei",
                        "opening_hours": "Mo-Fr 08:00-18:00; Sa 09:00-18:00",
                        "website": "https://www.rada-heidelberg.de/"
                      }
                    }
                  ]
                }
                """;

        // Mock WebClient chain
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        // Act
        OsmNode result = osmDataService.fetchNode(nodeId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.nodeId()).isEqualTo(nodeId);
        assertThat(result.lat()).isEqualTo(49.4130716);
        assertThat(result.lon()).isEqualTo(8.6911353);
        assertThat(result.tags()).isNotNull();
        assertThat(result.tags().get("name")).isEqualTo("Rada Coffee & Rösterei");
        assertThat(result.tags().get("amenity")).isEqualTo("cafe");
        assertThat(result.tags().get("addr:street")).isEqualTo("Untere Straße");
        assertThat(result.tags().get("addr:housenumber")).isEqualTo("21");
        assertThat(result.tags().get("addr:postcode")).isEqualTo("69117");
        assertThat(result.tags().get("addr:city")).isEqualTo("Heidelberg");

        // Verify WebClient was called correctly
        verify(webClient).get();
        verify(requestHeadersUriSpec).uri("/node/" + nodeId + ".json");
        verify(requestHeadersSpec).retrieve();
        verify(responseSpec).bodyToMono(String.class);
    }

    @Test
    void fetchNode_shouldThrowOsmNodeNotFoundException_whenNodeDoesNotExist() {
        // Arrange: Mock 404 response
        Long nodeId = 999999999L;
        WebClientResponseException notFoundException = WebClientResponseException.create(
                404,
                "Not Found",
                null,
                null,
                null
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(notFoundException));

        // Act & Assert
        assertThatThrownBy(() -> osmDataService.fetchNode(nodeId))
                .isInstanceOf(OsmNodeNotFoundException.class)
                .hasMessageContaining("" + nodeId);
    }

    @Test
    void fetchNode_shouldThrowOsmNodeNotFoundException_whenApiReturnsServerError() {
        // Arrange: Mock 500 response
        Long nodeId = 5589879349L;
        WebClientResponseException serverError = WebClientResponseException.create(
                500,
                "Internal Server Error",
                null,
                null,
                null
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(serverError));

        // Act & Assert
        assertThatThrownBy(() -> osmDataService.fetchNode(nodeId))
                .isInstanceOf(OsmNodeNotFoundException.class)
                .hasMessageContaining("" + nodeId);
    }

    @Test
    void fetchNode_shouldThrowOsmNodeNotFoundException_whenJsonParsingFails() {
        // Arrange: Mock invalid JSON response
        Long nodeId = 5589879349L;
        String invalidJson = "{ invalid json }";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(invalidJson));

        // Act & Assert
        assertThatThrownBy(() -> osmDataService.fetchNode(nodeId))
                .isInstanceOf(OsmNodeNotFoundException.class)
                .hasMessageContaining("" + nodeId);
    }

    @Test
    void fetchNode_shouldThrowOsmNodeNotFoundException_whenElementsArrayIsEmpty() {
        // Arrange: Mock response with empty elements array
        Long nodeId = 5589879349L;
        String jsonResponse = """
                {
                  "version": "0.6",
                  "generator": "CGImap 0.9.5",
                  "elements": []
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        // Act & Assert
        assertThatThrownBy(() -> osmDataService.fetchNode(nodeId))
                .isInstanceOf(OsmNodeNotFoundException.class)
                .hasMessageContaining("" + nodeId);
    }

    @Test
    void fetchNode_shouldHandleNodeWithMinimalTags() {
        // Arrange: Mock response with minimal required fields
        Long nodeId = 1234567L;
        String jsonResponse = """
                {
                  "version": "0.6",
                  "generator": "CGImap 0.9.5",
                  "elements": [
                    {
                      "type": "node",
                      "id": 1234567,
                      "lat": 49.4,
                      "lon": 8.7,
                      "tags": {
                        "name": "Test Cafe"
                      }
                    }
                  ]
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        // Act
        OsmNode result = osmDataService.fetchNode(nodeId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.nodeId()).isEqualTo(nodeId);
        assertThat(result.lat()).isEqualTo(49.4);
        assertThat(result.lon()).isEqualTo(8.7);
        assertThat(result.tags()).hasSize(1);
        assertThat(result.tags().get("name")).isEqualTo("Test Cafe");
    }
}
