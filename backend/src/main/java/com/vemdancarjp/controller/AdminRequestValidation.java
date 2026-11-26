package com.vemdancarjp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

final class AdminRequestValidation {

    private static final Pattern RESOURCE_ID = Pattern.compile("[1-9][0-9]{0,18}");

    private AdminRequestValidation() {
    }

    static Long rawId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String id = uri.substring(uri.lastIndexOf('/') + 1);
        if (!RESOURCE_ID.matcher(id).matches()) {
            throw new InvalidAdminRequestException();
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new InvalidAdminRequestException();
        }
    }

    static void requireObjectWithFields(JsonNode body, Set<String> allowedFields) {
        if (body == null || !body.isObject()) {
            throw new InvalidAdminRequestException();
        }
        Iterator<String> fields = body.fieldNames();
        while (fields.hasNext()) {
            if (!allowedFields.contains(fields.next())) {
                throw new InvalidAdminRequestException();
            }
        }
    }

    static String optionalText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new InvalidAdminRequestException();
        }
        String value = node.textValue().strip();
        if (value.indexOf('\0') >= 0) {
            throw new InvalidAdminRequestException();
        }
        return value.isEmpty() ? null : value;
    }

    static String requiredText(JsonNode body, String field) {
        String value = optionalText(body, field);
        if (value == null) {
            throw new InvalidAdminRequestException();
        }
        return value;
    }

    static String optionalInstagram(JsonNode body) {
        String instagram = optionalText(body, "instagram");
        if (instagram != null && !validHttpUrl(instagram)) {
            throw new InvalidAdminRequestException();
        }
        return instagram;
    }

    static int sortOrder(JsonNode body) {
        JsonNode sortOrder = body.get("sortOrder");
        if (sortOrder == null || !sortOrder.isIntegralNumber() || !sortOrder.canConvertToInt()) {
            throw new InvalidAdminRequestException();
        }
        return sortOrder.intValue();
    }

    private static boolean validHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && uri.getHost() != null &&
                ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static final class InvalidAdminRequestException extends RuntimeException {
    }
}
