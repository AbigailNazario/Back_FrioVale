package com.pi.frioVale.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Alerta de impacto acima do limite configurado.
 *
 * <p>Entidade separada de {@link Alert} porque o Alert exige uma telemetria
 * (OneToOne obrigatória) e um impacto não possui telemetria térmica associada.
 * Um ImpactAlert por ImpactEvent (unicidade por impact_event_id) garante que
 * reprocessamentos não gerem alertas duplicados.</p>
 */
@Entity
@Table(name = "impact_alerts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_impact_alert_event",
                columnNames = {"impact_event_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "impact_event_id", nullable = false, unique = true)
    private ImpactEvent impactEvent;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private Boolean resolved = Boolean.FALSE;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
