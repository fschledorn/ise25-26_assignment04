package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

        // Convert OSM node to POS domain object and upsert it
        // TODO: Implement the actual conversion (the response is currently hard-coded).
        Pos savedPos = upsert(convertOsmNodeToPos(osmNode));
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);

        return savedPos;
    }

    /**
     * Converts an OSM node to a POS domain object.
     * <p>
     * Maps OSM tags to POS fields with validation:
     * <ul>
     *   <li>name: Required from tags["name"]</li>
     *   <li>type: Derived from tags["amenity"] (cafe→CAFE, bakery→BAKERY, restaurant→CAFE)</li>
     *   <li>address: Required from tags["addr:street"], tags["addr:housenumber"],
     *       tags["addr:postcode"], tags["addr:city"]</li>
     *   <li>campus: Derived from postal code (69117→ALTSTADT, 69120→INF)</li>
     *   <li>description: From tags["description"] or generated from tags["cuisine"]</li>
     * </ul>
     *
     * @param osmNode The OSM node to convert
     * @return POS domain object ready for persistence
     * @throws OsmNodeMissingFieldsException if required fields are missing or amenity type is invalid
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) {
        log.debug("Converting OSM node {} to POS", osmNode.nodeId());

        var tags = osmNode.tags();

        // Extract and validate required fields
        String name = tags.get("name");
        if (name == null || name.isBlank()) {
            log.error("OSM node {} missing required 'name' tag", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Map amenity to PosType
        String amenity = tags.get("amenity");
        PosType type = mapAmenityToPosType(amenity, osmNode.nodeId());

        // Extract and validate address fields
        String street = tags.get("addr:street");
        String houseNumber = tags.get("addr:housenumber");
        String postalCodeStr = tags.get("addr:postcode");
        String city = tags.get("addr:city");

        if (street == null || street.isBlank() ||
            houseNumber == null || houseNumber.isBlank() ||
            postalCodeStr == null || postalCodeStr.isBlank() ||
            city == null || city.isBlank()) {
            log.error("OSM node {} missing required address fields", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        Integer postalCode;
        try {
            postalCode = Integer.parseInt(postalCodeStr);
        } catch (NumberFormatException e) {
            log.error("OSM node {} has invalid postal code: {}", osmNode.nodeId(), postalCodeStr);
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Derive campus from postal code
        CampusType campus = deriveCampusFromPostalCode(postalCode, osmNode);

        // Extract or generate description
        String description = tags.get("description");
        if (description == null || description.isBlank()) {
            String cuisine = tags.get("cuisine");
            if (cuisine != null && !cuisine.isBlank()) {
                description = String.format("A %s serving %s cuisine", amenity != null ? amenity : "place", cuisine);
            } else {
                description = String.format("A %s in %s", amenity != null ? amenity : "place", city);
            }
        }

        log.debug("Successfully mapped OSM node {} to POS '{}'", osmNode.nodeId(), name);

        return Pos.builder()
                .name(name)
                .description(description)
                .type(type)
                .campus(campus)
                .street(street)
                .houseNumber(houseNumber)
                .postalCode(postalCode)
                .city(city)
                .build();
    }

    /**
     * Maps OSM amenity tag to PosType enum.
     * Supports: cafe, bakery, restaurant (mapped to CAFE).
     *
     * @param amenity The OSM amenity value
     * @param nodeId  The node ID for error messages
     * @return Mapped PosType
     * @throws OsmNodeMissingFieldsException if amenity is invalid or unsupported
     */
    private @NonNull PosType mapAmenityToPosType(String amenity, @NonNull Long nodeId) {
        if (amenity == null || amenity.isBlank()) {
            log.error("OSM node {} missing 'amenity' tag", nodeId);
            throw new OsmNodeMissingFieldsException(nodeId);
        }

        return switch (amenity.toLowerCase()) {
            case "cafe", "restaurant" -> PosType.CAFE;
            case "bakery" -> PosType.BAKERY;
            default -> {
                log.error("OSM node {} has unsupported amenity type: {}", nodeId, amenity);
                throw new OsmNodeMissingFieldsException(nodeId);
            }
        };
    }

    /**
     * Derives campus from postal code.
     * Supports Heidelberg postal codes: 69117 (ALTSTADT), 69120 (INF).
     *
     * @param postalCode The postal code
     * @param osmNode    The OSM node (for lat/lon fallback if needed)
     * @return Derived CampusType
     * @throws OsmNodeMissingFieldsException if postal code doesn't match known campuses
     */
    private @NonNull CampusType deriveCampusFromPostalCode(Integer postalCode, @NonNull OsmNode osmNode) {
        return switch (postalCode) {
            case 69117 -> CampusType.ALTSTADT;
            case 69120 -> CampusType.INF;
            default -> {
                log.error("OSM node {} has postal code {} that doesn't match known campuses",
                        osmNode.nodeId(), postalCode);
                throw new OsmNodeMissingFieldsException(osmNode.nodeId());
            }
        };
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}
