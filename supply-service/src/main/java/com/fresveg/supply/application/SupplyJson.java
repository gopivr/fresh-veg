package com.fresveg.supply.application;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SupplyJson {
    private final JsonMapper mapper;
    public SupplyJson(JsonMapper mapper) { this.mapper=mapper; }
    public String attributes(Map<String,Object> value) { return validate(mapper.valueToTree(value)); }
    public String filter(String value) {
        if (value==null) { return null; }
        try {
            if (value.length()>16384) { throw invalid(); }
            return validate(mapper.readTree(value));
        } catch (Exception error) { throw invalid(); }
    }
    private String validate(JsonNode value) {
        if (value==null || !value.isObject() || value.size()>64) { throw invalid(); }
        checkDepth(value,0);
        String json=mapper.writeValueAsString(value);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384) { throw invalid(); }
        return json;
    }
    private void checkDepth(JsonNode node,int depth) {
        if (depth>8) { throw invalid(); }
        if ((node.isObject() || node.isArray())) { for (var child:node) { checkDepth(child,depth+1); } }
    }
    private static SupplyException invalid() {
        return new SupplyException(HttpStatus.BAD_REQUEST,"SUP-400-003","Attributes must be a JSON object of at most 16 KiB, 64 top-level keys and depth 8.");
    }
}
