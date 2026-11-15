package com.pi.frioVale.dto;

import com.pi.frioVale.domain.Container;

public record ContainerResponse(
        Long id,
        String code,
        String thingSpeakChannelId,
        boolean available
) {
    public static ContainerResponse from(Container c, boolean available) {
        return new ContainerResponse(c.getId(), c.getCode(), c.getThingSpeakChannelId(), available);
    }
}
