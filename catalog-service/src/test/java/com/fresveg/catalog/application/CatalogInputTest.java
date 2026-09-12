package com.fresveg.catalog.application;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CatalogInputTest {
    private final JsonMapper mapper=new JsonMapper();
    @Test
    void cursorRoundTripsUnicodeAndRejectsChangedFiltersAndMalformedInput() {
        var cursors=new CatalogCursor(mapper); UUID id=UUID.randomUUID();
        String cursor=cursors.encode("filter","Tomate Écarlate",id);
        assertThat(cursors.decode(cursor,"filter").id()).isEqualTo(id);
        assertThat(cursors.decode(cursor,"filter").value()).isEqualTo("Tomate Écarlate");
        for (String bad:List.of("!invalid", "x".repeat(2049))) {
            assertThatThrownBy(() -> cursors.decode(bad,"filter")).isInstanceOf(CatalogException.class);
        }
        assertThatThrownBy(() -> cursors.decode(cursor,"other")).isInstanceOf(CatalogException.class);
    }
    @Test
    void metadataLimitsAccountForUtf8BytesDepthAndTopLevelShape() {
        var json=new CatalogJson(mapper);
        assertThat(json.attributes(Map.of("origin","US","certified",true))).contains("origin");
        assertThatThrownBy(() -> json.filter("[]")).isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> json.attributes(Map.of("text","界".repeat(6000)))).isInstanceOf(CatalogException.class);
        Map<String,Object> nested=Map.of("leaf",true);
        for (int i=0;i<10;i++) { nested=Map.of("nested",nested); }
        var deep=nested;
        assertThatThrownBy(() -> json.attributes(deep)).isInstanceOf(CatalogException.class);
        var wide=new LinkedHashMap<String,Object>();
        for (int i=0;i<65;i++) { wide.put("key"+i,i); }
        assertThatThrownBy(() -> json.attributes(wide)).isInstanceOf(CatalogException.class);
    }
}
