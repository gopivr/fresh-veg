package com.fresveg.supply.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SupplyCursor {
    private final JsonMapper mapper;
    public SupplyCursor(JsonMapper mapper) { this.mapper=mapper; }
    public String fingerprint(Object filters) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(filters)));
        } catch (Exception error) { throw new IllegalStateException("Cannot encode supply filters", error); }
    }
    public String encode(String filter, String value, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(new Position(filter,value,id)));
    }
    public Position decode(String cursor, String expectedFilter) {
        if (cursor==null) { return null; }
        try {
            if (cursor.length()>2048) { throw new IllegalArgumentException(); }
            var position=mapper.readValue(new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8),Position.class);
            if (!expectedFilter.equals(position.filter()) || position.id()==null || position.value()==null || position.value().length()>160) {
                throw new IllegalArgumentException();
            }
            return position;
        } catch (Exception error) {
            throw new SupplyException(HttpStatus.BAD_REQUEST,"SUP-400-002","Invalid cursor or cursor filters have changed.");
        }
    }
    public record Position(String filter, String value, UUID id) { }
}
