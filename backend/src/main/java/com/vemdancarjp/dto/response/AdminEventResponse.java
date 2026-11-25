package com.vemdancarjp.dto.response;

import com.vemdancarjp.entity.Event;

public record AdminEventResponse(
    Long id,
    String dayId,
    String name,
    String venue,
    String neighborhood,
    String instagram,
    String frequency,
    Integer sortOrder
) {
    public AdminEventResponse(Event event) {
        this(event.getId(), event.getDayId(), event.getName(), event.getVenue(),
            event.getNeighborhood(), event.getInstagram(), event.getFrequency(), event.getSortOrder());
    }
}
