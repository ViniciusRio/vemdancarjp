package com.vemdancarjp.dto.response;

import com.vemdancarjp.entity.VariableVenue;

public record AdminVariableVenueResponse(
    Long id,
    String name,
    String neighborhood,
    String instagram,
    String days,
    Integer sortOrder
) {
    public AdminVariableVenueResponse(VariableVenue venue) {
        this(venue.getId(), venue.getName(), venue.getNeighborhood(), venue.getInstagram(),
            venue.getDays(), venue.getSortOrder());
    }
}
