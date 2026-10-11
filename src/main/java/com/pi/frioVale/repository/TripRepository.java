package com.pi.frioVale.repository;

import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.enums.TripStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    @EntityGraph(attributePaths = {"container", "driver", "fruit"})
    List<Trip> findByStatus(TripStatus status);

    @EntityGraph(attributePaths = {"container", "driver", "fruit"})
    Optional<Trip> findDetailedById(Long id);

    boolean existsByContainerIdAndStatus(Long containerId, TripStatus status);
}
