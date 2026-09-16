package com.vemdancarjp.dto.response;

import java.util.List;

public record DayResponse(
    String id,
    String label,
    List<EventResponse> events
) {}
