package serwio.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import serwio.order.model.OrderType;

public record ServiceOrderRequest(
        @NotNull UUID serviceObjectId,
        @NotNull OrderType orderType,
        @NotBlank @Size(max = 100000) String description) {}
