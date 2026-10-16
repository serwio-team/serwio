package serwio.asset.dto;

import java.util.List;

public record ServiceObjectPageResponse(
        List<ServiceObjectListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
