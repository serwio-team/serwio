package serwio.location.dto;

import java.util.List;

public record LocationPageResponse(
        List<LocationListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
