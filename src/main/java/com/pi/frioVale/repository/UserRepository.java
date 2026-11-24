package com.pi.frioVale.repository;

import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    List<User> findByRoleOrderByNameAsc(Role role);

    Optional<User> findFirstByRoleOrderByIdAsc(Role role);

    Optional<User> findFirstByOrderByIdAsc();

    /**
     * [Seguranca - 2FA] Le o usuario com bloqueio de linha durante a verificacao do codigo,
     * para que duas requisicoes simultaneas com o mesmo codigo nao passem pela checagem de replay.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdateById(@Param("id") Long id);
}
