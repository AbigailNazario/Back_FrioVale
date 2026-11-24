package com.pi.frioVale.seguranca;

import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.Fruit;
import com.pi.frioVale.domain.Telemetry;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.FruitRepository;
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.TripRepository;
import com.pi.frioVale.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

/**
 * Cenario sintetico usado nos testes de seguranca:
 * - Gestor (ROLE_COMPANY)
 * - Motorista A, dono da viagem A no conteiner A (dispositivo A)
 * - Motorista B, dono da viagem B no conteiner B (dispositivo B)
 * Nenhum dado pessoal real.
 */
public class SyntheticData {

    public static final String PASSWORD = "SenhaSintetica#2026";

    public User gestor;
    public User motoristaA;
    public User motoristaB;
    public Container containerA;
    public Container containerB;
    public Trip tripA;
    public Trip tripB;

    public static SyntheticData create(UserRepository users, ContainerRepository containers, FruitRepository fruits,
                                       TripRepository trips, TelemetryRepository telemetry, PasswordEncoder encoder,
                                       String suffix) {
        SyntheticData d = new SyntheticData();
        d.gestor = users.save(user("Gestor Teste " + suffix, "gestor." + suffix + "@teste.local", Role.ROLE_COMPANY, encoder));
        d.motoristaA = users.save(user("Motorista A " + suffix, "motorista.a." + suffix + "@teste.local", Role.ROLE_DRIVER, encoder));
        d.motoristaB = users.save(user("Motorista B " + suffix, "motorista.b." + suffix + "@teste.local", Role.ROLE_DRIVER, encoder));

        d.containerA = containers.save(Container.builder().code("CONT-A-" + suffix).thingSpeakChannelId("A" + suffix).build());
        d.containerB = containers.save(Container.builder().code("CONT-B-" + suffix).thingSpeakChannelId("B" + suffix).build());

        Fruit fruit = fruits.findAll().stream().findFirst().orElseGet(() -> fruits.save(Fruit.builder()
                .name("Uva").minTemp(2.0).maxTemp(8.0).minHumidity(85.0).maxHumidity(95.0).build()));

        LocalDateTime start = LocalDateTime.now().minusHours(1);
        d.tripA = trips.save(Trip.builder().container(d.containerA).driver(d.motoristaA).fruit(fruit)
                .origin("Petrolina").destination("Recife").status(TripStatus.IN_TRANSIT).startDate(start).build());
        d.tripB = trips.save(Trip.builder().container(d.containerB).driver(d.motoristaB).fruit(fruit)
                .origin("Juazeiro").destination("Salvador").status(TripStatus.IN_TRANSIT).startDate(start).build());

        telemetry.save(Telemetry.builder().trip(d.tripA).temperature(5.0).humidity(90.0)
                .timestamp(LocalDateTime.now().minusMinutes(5)).isAlert(false).build());
        telemetry.save(Telemetry.builder().trip(d.tripB).temperature(6.5).humidity(88.0)
                .timestamp(LocalDateTime.now().minusMinutes(5)).isAlert(false).build());
        return d;
    }

    private static User user(String name, String email, Role role, PasswordEncoder encoder) {
        return User.builder().name(name).email(email).role(role).password(encoder.encode(PASSWORD)).build();
    }
}
