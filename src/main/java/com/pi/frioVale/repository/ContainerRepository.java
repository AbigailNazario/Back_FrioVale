package com.pi.frioVale.repository;

import com.pi.frioVale.domain.Container;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContainerRepository extends JpaRepository<Container, Long> {
    Optional<Container> findByCode(String code);
}
