package com.vemdancarjp.repository;

import com.vemdancarjp.entity.PendingAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PendingAdminRepository extends JpaRepository<PendingAdmin, UUID> {
}
