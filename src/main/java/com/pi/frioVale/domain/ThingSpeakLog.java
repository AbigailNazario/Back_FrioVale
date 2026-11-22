package com.pi.frioVale.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Log bruto de uma entrada lida do canal ThingSpeak do dispositivo IoT.
 * É mantido separado da telemetria para preservar o histórico recebido mesmo
 * quando não existe uma viagem em andamento para o contêiner.
 */
@Entity
@Table(name = "thingspeak_logs",
        indexes = {
                @Index(name = "idx_tslog_recorded_at", columnList = "recorded_at"),
                @Index(name = "idx_tslog_container", columnList = "container_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tslog_channel_entry",
                columnNames = {"channel_id", "entry_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThingSpeakLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Contêiner associado ao canal (pode ser nulo se o contêiner for removido). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_id")
    private Container container;

    @Column(name = "channel_id", nullable = false, length = 40)
    private String channelId;

    /** entry_id do ThingSpeak; garante que a mesma entrada não seja gravada duas vezes. */
    @Column(name = "entry_id")
    private Long entryId;

    private Double temperature;

    private Double humidity;

    private Double latitude;

    private Double longitude;

    /** field3: maior pico de aceleração dinâmica do intervalo, em g (zero é preservado). */
    @Column(name = "peak_g")
    private Double peakG;

    /** field4: inclinação medida pelo acelerômetro, em graus. */
    @Column(name = "tilt_deg")
    private Double tiltDeg;

    /** field5: contador acumulado de impactos na sessão do ESP32 (não somar entre leituras). */
    @Column(name = "session_impact_count")
    private Double sessionImpactCount;

    /** Momento da leitura no ThingSpeak, convertido para o fuso da aplicação. */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    /** Momento em que o backend gravou o log. */
    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
