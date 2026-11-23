package serwio.visit.dto;

import java.util.List;
import serwio.location.dto.AvailabilityRequest;

public record ServiceVisitsResponse(String timezone, List<AvailabilityRequest.Day> availability,
                                    List<ServiceVisitResponse> visits) {}
