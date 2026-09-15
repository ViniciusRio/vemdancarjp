package com.vemdancarjp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "variable_venues")
public class VariableVenue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String neighborhood;

    @Column(nullable = true)
    private String instagram;

    @Column(nullable = false)
    private String days;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
