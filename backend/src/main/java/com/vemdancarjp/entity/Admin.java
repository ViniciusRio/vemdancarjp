package com.vemdancarjp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "admins")
public class Admin {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;
}
