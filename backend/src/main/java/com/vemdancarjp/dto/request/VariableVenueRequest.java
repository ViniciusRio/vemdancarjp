package com.vemdancarjp.dto.request;

public record VariableVenueRequest(
    String name,
    String neighborhood,
    String instagram,
    String days,
    int sortOrder
) {}
