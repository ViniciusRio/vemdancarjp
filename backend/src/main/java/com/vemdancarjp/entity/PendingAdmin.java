package com.vemdancarjp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "pending_admins")
public class PendingAdmin {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true)
    private String name;

    @Column(name = "requested_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ NOT NULL DEFAULT now()")
    private Instant requestedAt;
}
