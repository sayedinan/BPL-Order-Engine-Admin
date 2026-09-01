package com.BPL_Order_Engine_Admin.manager.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Case-insensitive username lookup at auth time (per SPEC §3.2). */
    Optional<User> findByUsernameIgnoreCase(String username);
}
