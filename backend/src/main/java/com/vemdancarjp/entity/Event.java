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
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_id", nullable = false, length = 10)
    private String dayId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private String neighborhood;

    @Column(nullable = true)
    private String instagram;

    @Column(nullable = true)
    private String frequency;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
