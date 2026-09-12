package com.fresveg.account.api.dto;

import java.util.List;
import java.util.UUID;

public record CollectionResponse<T>(List<T> data, Pagination pagination, ApiResponse.Meta meta) {
    public record Pagination(UUID nextCursor, boolean hasNext) { }
}
