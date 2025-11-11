package de.seuhd.campuscoffee.domain.model;

import lombok.Builder;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Represents an OpenStreetMap node with relevant Point of Sale information.
 * This is the domain model for OSM data before it is converted to a POS object.
 * <p>
 * OSM nodes contain geographical coordinates and a flexible tag system for metadata.
 * Tags are key-value pairs like "name", "amenity", "addr:street", etc.
 *
 * @param nodeId The OpenStreetMap node ID
 * @param lat    Latitude coordinate in decimal degrees
 * @param lon    Longitude coordinate in decimal degrees
 * @param tags   Map of OSM tags (key-value pairs) containing metadata like name, address, amenity type
 */
@Builder
public record OsmNode(
        @NonNull Long nodeId,
        @NonNull Double lat,
        @NonNull Double lon,
        @NonNull Map<String, String> tags
) {
}
