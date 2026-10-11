package com.pi.frioVale.repository;

import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    List<User> findByRoleOrderByNameAsc(Role role);
}
