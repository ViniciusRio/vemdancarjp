package com.vemdancarjp.dto.request;

public record CreateEventRequest(
    String dayId,
    String name,
    String venue,
    String neighborhood,
    String instagram,
    String frequency,
    Integer sortOrder
) {}
