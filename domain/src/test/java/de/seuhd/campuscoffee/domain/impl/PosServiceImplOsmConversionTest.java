package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OSM to POS conversion logic in PosServiceImpl.
 * Tests the mapping of OSM tags to POS fields and validation of required fields.
 */
@ExtendWith(MockitoExtension.class)
class PosServiceImplOsmConversionTest {

    @Mock
    private OsmDataService osmDataService;

    @Mock
    private PosDataService posDataService;

    private PosServiceImpl posService;

    @BeforeEach
    void setUp() {
        posService = new PosServiceImpl(posDataService, osmDataService);
    }

    @Test
    void importFromOsmNode_shouldMapAllFieldsCorrectly_whenAllTagsPresent() {
        // Arrange: OSM node with complete cafe data
        Long nodeId = 5589879349L;
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "Rada Coffee & Rösterei");
        tags.put("amenity", "cafe");
        tags.put("cuisine", "coffee_shop");
        tags.put("addr:street", "Untere Straße");
        tags.put("addr:housenumber", "21");
        tags.put("addr:postcode", "69117");
        tags.put("addr:city", "Heidelberg");

        OsmNode osmNode = OsmNode.builder()
                .nodeId(nodeId)
                .lat(49.4130716)
                .lon(8.6911353)
                .tags(tags)
                .build();

        when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);
        when(posDataService.upsert(any(Pos.class))).thenAnswer(invocation -> {
            Pos pos = invocation.getArgument(0);
            return pos.toBuilder().id(1L).build();
        });

        // Act
        Pos result = posService.importFromOsmNode(nodeId);

        // Assert
        ArgumentCaptor<Pos> posCaptor = ArgumentCaptor.forClass(Pos.class);
        verify(posDataService).upsert(posCaptor.capture());
        Pos capturedPos = posCaptor.getValue();

        assertThat(capturedPos.name()).isEqualTo("Rada Coffee & Rösterei");
        assertThat(capturedPos.type()).isEqualTo(PosType.CAFE);
        assertThat(capturedPos.street()).isEqualTo("Untere Straße");
        assertThat(capturedPos.houseNumber()).isEqualTo("21");
        assertThat(capturedPos.postalCode()).isEqualTo(69117);
        assertThat(capturedPos.city()).isEqualTo("Heidelberg");
        assertThat(capturedPos.campus()).isEqualTo(CampusType.ALTSTADT);
        assertThat(capturedPos.description()).contains("coffee_shop");

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void importFromOsmNode_shouldThrowException_whenNameTagMissing() {
        // Arrange: OSM node without name tag
        Long nodeId = 123456L;
        Map<String, String> tags = new HashMap<>();
        tags.put("amenity", "cafe");
        tags.put("addr:street", "Test Street");

        OsmNode osmNode = OsmNode.builder()
                .nodeId(nodeId)
                .lat(49.4)
                .lon(8.7)
                .tags(tags)
                .build();

        when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);

        // Act & Assert
        assertThatThrownBy(() -> posService.importFromOsmNode(nodeId))
                .isInstanceOf(OsmNodeMissingFieldsException.class)
                .hasMessageContaining("" + nodeId);

        verify(posDataService, never()).upsert(any());
    }

    @Test
    void importFromOsmNode_shouldThrowException_whenAmenityIsNotCafeRelated() {
        // Arrange: OSM node with non-cafe amenity
        Long nodeId = 123456L;
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "Some Place");
        tags.put("amenity", "bank");  // Not a cafe/restaurant/bakery
        tags.put("addr:street", "Test Street");
        tags.put("addr:housenumber", "1");
        tags.put("addr:postcode", "69117");
        tags.put("addr:city", "Heidelberg");

        OsmNode osmNode = OsmNode.builder()
                .nodeId(nodeId)
                .lat(49.4)
                .lon(8.7)
                .tags(tags)
                .build();

        when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);

        // Act & Assert
        assertThatThrownBy(() -> posService.importFromOsmNode(nodeId))
                .isInstanceOf(OsmNodeMissingFieldsException.class)
                .hasMessageContaining("" + nodeId);

        verify(posDataService, never()).upsert(any());
    }

    @Test
    void importFromOsmNode_shouldThrowException_whenAddressFieldsMissing() {
        // Arrange: OSM node without complete address
        Long nodeId = 123456L;
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "Incomplete Cafe");
        tags.put("amenity", "cafe");
        tags.put("addr:street", "Test Street");
        // Missing housenumber, postcode, city

        OsmNode osmNode = OsmNode.builder()
                .nodeId(nodeId)
                .lat(49.4)
                .lon(8.7)
                .tags(tags)
                .build();

        when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);

        // Act & Assert
        assertThatThrownBy(() -> posService.importFromOsmNode(nodeId))
                .isInstanceOf(OsmNodeMissingFieldsException.class)
                .hasMessageContaining("" + nodeId);

        verify(posDataService, never()).upsert(any());
    }

    @Test
    void importFromOsmNode_shouldMapBakeryType_whenAmenityIsBakery() {
        // Arrange
        Long nodeId = 123456L;
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "Test Bakery");
        tags.put("amenity", "bakery");
        tags.put("addr:street", "Baker Street");
        tags.put("addr:housenumber", "221B");
        tags.put("addr:postcode", "69120");
        tags.put("addr:city", "Heidelberg");

        OsmNode osmNode = OsmNode.builder()
                .nodeId(nodeId)
                .lat(49.4)
                .lon(8.7)
                .tags(tags)
                .build();

        when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);
        when(posDataService.upsert(any(Pos.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        posService.importFromOsmNode(nodeId);

        // Assert
        ArgumentCaptor<Pos> posCaptor = ArgumentCaptor.forClass(Pos.class);
        verify(posDataService).upsert(posCaptor.capture());
        assertThat(posCaptor.getValue().type()).isEqualTo(PosType.BAKERY);
    }

    @Test
    void importFromOsmNode_shouldMapRestaurantToCafe_whenAmenityIsRestaurant() {
        // Arrange
        Long nodeId = 123456L;
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "Test Restaurant");
        tags.put("amenity", "restaurant");
        tags.put("cuisine", "coffee_shop");
        tags.put("addr:street", "Restaurant Alley");
        tags.put("addr:housenumber", "42");
        tags.put("addr:postcode", "69117");
        tags.put("addr:city", "Heidelberg");

        OsmNode osmNode = OsmNode.builder()
                .nodeId(nodeId)
                .lat(49.41)
                .lon(8.69)
                .tags(tags)
                .build();

        when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);
        when(posDataService.upsert(any(Pos.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        posService.importFromOsmNode(nodeId);

        // Assert
        ArgumentCaptor<Pos> posCaptor = ArgumentCaptor.forClass(Pos.class);
        verify(posDataService).upsert(posCaptor.capture());
        assertThat(posCaptor.getValue().type()).isEqualTo(PosType.CAFE);
    }

    @Test
    void importFromOsmNode_shouldDeriveCampusFromPostalCode() {
        // Arrange: Test different postal codes for campus derivation
        Long nodeId = 123456L;
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "INF Cafe");
        tags.put("amenity", "cafe");
        tags.put("addr:street", "Im Neuenheimer Feld");
        tags.put("addr:housenumber", "370");
        tags.put("addr:postcode", "69120");  // INF campus
        tags.put("addr:city", "Heidelberg");

        OsmNode osmNode = OsmNode.builder()
                .nodeId(nodeId)
                .lat(49.417)
                .lon(8.673)
                .tags(tags)
                .build();

        when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);
        when(posDataService.upsert(any(Pos.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        posService.importFromOsmNode(nodeId);

        // Assert
        ArgumentCaptor<Pos> posCaptor = ArgumentCaptor.forClass(Pos.class);
        verify(posDataService).upsert(posCaptor.capture());
        assertThat(posCaptor.getValue().campus()).isEqualTo(CampusType.INF);
    }

    @Test
    void importFromOsmNode_shouldGenerateDescription_whenDescriptionTagMissing() {
        // Arrange
        Long nodeId = 123456L;
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "Simple Cafe");
        tags.put("amenity", "cafe");
        tags.put("addr:street", "Simple Street");
        tags.put("addr:housenumber", "1");
        tags.put("addr:postcode", "69117");
        tags.put("addr:city", "Heidelberg");
        // No description or cuisine tag

        OsmNode osmNode = OsmNode.builder()
                .nodeId(nodeId)
                .lat(49.4)
                .lon(8.7)
                .tags(tags)
                .build();

        when(osmDataService.fetchNode(nodeId)).thenReturn(osmNode);
        when(posDataService.upsert(any(Pos.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        posService.importFromOsmNode(nodeId);

        // Assert
        ArgumentCaptor<Pos> posCaptor = ArgumentCaptor.forClass(Pos.class);
        verify(posDataService).upsert(posCaptor.capture());
        assertThat(posCaptor.getValue().description())
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void importFromOsmNode_shouldPropagateOsmNodeNotFoundException() {
        // Arrange
        Long nodeId = 999999999L;
        when(osmDataService.fetchNode(nodeId)).thenThrow(new OsmNodeNotFoundException(nodeId));

        // Act & Assert
        assertThatThrownBy(() -> posService.importFromOsmNode(nodeId))
                .isInstanceOf(OsmNodeNotFoundException.class)
                .hasMessageContaining("" + nodeId);

        verify(posDataService, never()).upsert(any());
    }
}
