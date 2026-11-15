package com.pi.frioVale.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Resultado da predicao de IA associado a uma leitura de telemetria. */
@Entity
@Table(
        name = "ia_prediction_results",
        indexes = @Index(name = "idx_ia_pred_telemetry", columnList = "telemetry_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ia_pred_telemetry",
                columnNames = "telemetry_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IaPredictionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telemetry_id", nullable = false, unique = true)
    private Telemetry telemetry;

    @Column(nullable = false)
    private Boolean iaAnomalia;

    @Column(nullable = false, length = 20)
    private String iaResultado;

    @Column(length = 200)
    private String iaMensagem;

    @Column(length = 40)
    private String iaPerfil;

    @Column(nullable = false)
    private Boolean regrasAnomalia;

    @Column(nullable = false)
    private Boolean divergencia;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

}
