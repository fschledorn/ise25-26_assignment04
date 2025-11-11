package de.seuhd.campuscoffee.data.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link OsmDataService} that fetches OpenStreetMap node data
 * via the OSM API using WebClient.
 * <p>
 * This service calls the OSM API endpoint {@code /node/{id}.json} to retrieve
 * node information including coordinates and tags.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class OsmDataServiceImpl implements OsmDataService {

    private final WebClient osmWebClient;

    /**
     * Fetches an OpenStreetMap node by its ID from the OSM API.
     * <p>
     * Makes an HTTP GET request to {@code /node/{nodeId}.json} and parses the
     * JSON response to extract node data.
     *
     * @param nodeId The OSM node ID to fetch
     * @return OsmNode containing coordinates and tags
     * @throws OsmNodeNotFoundException if the node doesn't exist (404), the API returns
     *                                  an error (5xx), the response is invalid, or parsing fails
     */
    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Fetching OSM node data for node ID: {}", nodeId);

        try {
            OsmApiResponse response = osmWebClient
                    .get()
                    .uri("/node/{id}.json", nodeId)
                    .retrieve()
                    .bodyToMono(OsmApiResponse.class)
                    .block();

            if (response == null || response.elements() == null || response.elements().isEmpty()) {
                log.error("OSM API returned empty response for node ID: {}", nodeId);
                throw new OsmNodeNotFoundException(nodeId);
            }

            OsmElement element = response.elements().get(0);
            
            log.debug("Successfully fetched OSM node {} with {} tags", nodeId, 
                    element.tags() != null ? element.tags().size() : 0);

            return OsmNode.builder()
                    .nodeId(nodeId)
                    .lat(element.lat())
                    .lon(element.lon())
                    .tags(element.tags() != null ? element.tags() : Map.of())
                    .build();

        } catch (WebClientResponseException.NotFound e) {
            log.error("OSM node not found: {}", nodeId);
            throw new OsmNodeNotFoundException(nodeId);
        } catch (WebClientResponseException e) {
            log.error("OSM API error for node {}: {} {}", nodeId, e.getStatusCode(), e.getMessage());
            throw new OsmNodeNotFoundException(nodeId);
        } catch (Exception e) {
            log.error("Failed to fetch or parse OSM node {}: {}", nodeId, e.getMessage());
            throw new OsmNodeNotFoundException(nodeId);
        }
    }

    /**
     * DTO for OSM API JSON response structure.
     * OSM API returns: {"version": "0.6", "elements": [...]}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsmApiResponse(List<OsmElement> elements) {}

    /**
     * DTO for individual OSM element (node) in API response.
     * Contains node ID, coordinates, and tags.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsmElement(
            @JsonProperty("id") Long id,
            @JsonProperty("lat") Double lat,
            @JsonProperty("lon") Double lon,
            @JsonProperty("tags") Map<String, String> tags
    ) {}
}
