package com.vemdancarjp.dto.response;

import com.vemdancarjp.entity.OtherVenue;

public record AdminOtherVenueResponse(
    Long id,
    String name,
    String neighborhood,
    String instagram,
    Integer sortOrder
) {
    public AdminOtherVenueResponse(OtherVenue venue) {
        this(venue.getId(), venue.getName(), venue.getNeighborhood(), venue.getInstagram(), venue.getSortOrder());
    }
}
