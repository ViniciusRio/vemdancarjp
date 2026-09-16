package com.vemdancarjp.dto.response;

import java.util.List;

public record AgendaResponse(
    String city,
    String title,
    String subtitle,
    String lastUpdated,
    List<DayResponse> days,
    List<VariableVenueResponse> variableVenues,
    List<OtherVenueResponse> otherVenues
) {}
