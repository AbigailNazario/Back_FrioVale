package com.pi.frioVale.repository;

import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.enums.TripStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    @EntityGraph(attributePaths = {"container", "driver", "fruit"})
    List<Trip> findByStatus(TripStatus status);

    @EntityGraph(attributePaths = {"container", "driver", "fruit"})
    Optional<Trip> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"container", "driver", "fruit"})
    @Query("select t from Trip t order by t.id desc")
    List<Trip> findAllDetailed();

    boolean existsByContainerIdAndStatus(Long containerId, TripStatus status);

    /**
     * Viagem que estava em andamento no momento do impacto.
     * Usa startDate/endDate (e não apenas o status) para Associar impactos recebidos
     * com atraso mesmo depois da viagem ter sido concluída. Pageable(0,1) limita a 1.
     */
    @EntityGraph(attributePaths = {"container", "driver", "fruit"})
    @Query("""
            select t from Trip t
            where t.container.id = :containerId
              and t.startDate <= :moment
              and (t.endDate is null or t.endDate >= :moment)
            order by t.startDate desc
            """)
    List<Trip> findActiveAt(@Param("containerId") Long containerId,
                            @Param("moment") LocalDateTime moment,
                            Pageable pageable);

    // ---------------- [Seguranca - IDOR] consultas de dono ----------------

    boolean existsByIdAndDriverId(Long id, Long driverId);

    boolean existsByContainerIdAndDriverIdAndStatusIn(Long containerId, Long driverId, java.util.Collection<TripStatus> statuses);

    @Query("select t.id from Trip t where t.driver.id = :driverId")
    List<Long> findIdsByDriverId(@Param("driverId") Long driverId);

    @Query("select distinct t.container.id from Trip t where t.driver.id = :driverId and t.status in :statuses")
    List<Long> findContainerIdsByDriverIdAndStatusIn(@Param("driverId") Long driverId,
                                                     @Param("statuses") java.util.Collection<TripStatus> statuses);
}
