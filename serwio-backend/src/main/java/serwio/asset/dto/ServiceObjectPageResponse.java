package serwio.asset.dto;

import java.util.List;

public record ServiceObjectPageResponse(
        List<ServiceObjectResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
