package com.vemdancarjp.entity;

public enum DayOfWeek {
    MONDAY("monday", "Segunda-feira"),
    TUESDAY("tuesday", "Terça-feira"),
    WEDNESDAY("wednesday", "Quarta-feira"),
    THURSDAY("thursday", "Quinta-feira"),
    FRIDAY("friday", "Sexta-feira"),
    SATURDAY("saturday", "Sábado"),
    SUNDAY("sunday", "Domingo");

    private final String id;
    private final String label;

    DayOfWeek(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }
}
