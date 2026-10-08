package com.cinemahub.repository;

import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * DAO/Repository pattern: Spring Data JPA generates the implementation of
 * this interface at runtime, giving us CRUD (findAll, findById, save,
 * deleteById, ...) without writing any SQL or DAO boilerplate ourselves.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    /** Active accounts of one role - recipients of the movie emails (MovieEmailNotifier). */
    List<User> findByRoleAndStatus(Role role, UserStatus status);
}
