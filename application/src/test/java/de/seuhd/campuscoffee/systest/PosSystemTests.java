package de.seuhd.campuscoffee.systest;

import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import java.util.List;

import de.seuhd.campuscoffee.TestUtils;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for the operations related to POS (Point of Sale).
 */
public class PosSystemTests extends AbstractSysTest {

    @Test
    void createPos() {
        Pos posToCreate = TestFixtures.getPosFixturesForInsertion().getFirst();
        Pos createdPos = posDtoMapper.toDomain(TestUtils.createPos(List.of(posDtoMapper.fromDomain(posToCreate))).getFirst());

        assertThat(createdPos)
                .usingRecursiveComparison()
                .ignoringFields("id", "createdAt", "updatedAt") // prevent issues due to differing timestamps after conversions
                .isEqualTo(posToCreate);
    }

    @Test
    void getAllCreatedPos() {
        List<Pos> createdPosList = TestFixtures.createPosFixtures(posService);

        List<Pos> retrievedPos = TestUtils.retrievePos()
                .stream()
                .map(posDtoMapper::toDomain)
                .toList();

        assertThat(retrievedPos)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("createdAt", "updatedAt") // prevent issues due to differing timestamps after conversions
                .containsExactlyInAnyOrderElementsOf(createdPosList);
    }

    @Test
    void getPosById() {
        List<Pos> createdPosList = TestFixtures.createPosFixtures(posService);
        Pos createdPos = createdPosList.getFirst();

        Pos retrievedPos = posDtoMapper.toDomain(
                TestUtils.retrievePosById(createdPos.id())
        );

        assertThat(retrievedPos)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt") // prevent issues due to differing timestamps after conversions
                .isEqualTo(createdPos);
    }

    @Test
    void updatePos() {
        List<Pos> createdPosList = TestFixtures.createPosFixtures(posService);
        Pos posToUpdate = createdPosList.getFirst();

        // Update fields using toBuilder() pattern (records are immutable)
        posToUpdate = posToUpdate.toBuilder()
                .name(posToUpdate.name() + " (Updated)")
                .description("Updated description")
                .build();

        Pos updatedPos = posDtoMapper.toDomain(TestUtils.updatePos(List.of(posDtoMapper.fromDomain(posToUpdate))).getFirst());

        assertThat(updatedPos)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(posToUpdate);

        // Verify changes persist
        Pos retrievedPos = posDtoMapper.toDomain(TestUtils.retrievePosById(posToUpdate.id()));

        assertThat(retrievedPos)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(posToUpdate);
    }

    /**
     * Integration test for importing a POS from OpenStreetMap.
     * This test verifies the complete end-to-end flow:
     * 1. API endpoint receives OSM node ID
     * 2. Service fetches node data from OSM API
     * 3. OSM data is converted to POS entity
     * 4. POS is persisted to database
     * 5. Created POS is returned via API
     *
     * Note: This test uses the real OSM Data Service implementation.
     * In a production test suite, you might want to mock the external OSM API call
     * to avoid dependency on external services and ensure deterministic test results.
     */
    @Test
    void importPosFromOsmNode() {
        // Arrange: Use the example OSM node ID from the prompt (Rada Coffee & Rösterei)
        Long osmNodeId = 5589879349L;

        // Act: Import POS via API endpoint
        Pos importedPos = posDtoMapper.toDomain(TestUtils.importPosFromOsm(osmNodeId));

        // Assert: Verify the imported POS has expected values
        assertThat(importedPos).isNotNull();
        assertThat(importedPos.id()).isNotNull(); // Should have been assigned an ID
        assertThat(importedPos.name()).isEqualTo("Rada Coffee & Rösterei");
        assertThat(importedPos.type()).isIn(de.seuhd.campuscoffee.domain.model.PosType.CAFE);
        assertThat(importedPos.street()).isEqualTo("Untere Straße");
        assertThat(importedPos.houseNumber()).isEqualTo("21");
        assertThat(importedPos.postalCode()).isEqualTo(69117);
        assertThat(importedPos.city()).isEqualTo("Heidelberg");
        assertThat(importedPos.campus()).isNotNull();
        assertThat(importedPos.description()).isNotNull().isNotEmpty();
        assertThat(importedPos.createdAt()).isNotNull();
        assertThat(importedPos.updatedAt()).isNotNull();

        // Verify the POS can be retrieved by ID (persistence check)
        Pos retrievedPos = posDtoMapper.toDomain(TestUtils.retrievePosById(importedPos.id()));
        assertThat(retrievedPos)
                .usingRecursiveComparison()
                .isEqualTo(importedPos);
    }
}
