package com.pi.frioVale.security;

import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * [Seguranca - IDOR] Verificacao de dono feita no servidor, a cada requisicao.
 *
 * Quem e o usuario: SEMPRE o "sub" do JWT de acesso validado pelo Spring Security,
 * nunca um ID enviado pelo cliente (corpo, query ou cabecalho).
 *
 * Regra de dono no FrioVale:
 *  - Gestor (ROLE_COMPANY): administra a frota e acessa todos os dispositivos.
 *  - Motorista (ROLE_DRIVER): e dono das viagens atribuidas a ele (trip.driver) e, por elas,
 *    do conteiner/dispositivo IoT enquanto a viagem estiver PLANNED ou IN_TRANSIT.
 *
 * Quando bloqueia: grava o log com o codigo de missao e responde {@link #DENY_STATUS}.
 * O mesmo status e devolvido para recurso de outro usuario e para ID inexistente,
 * assim o atacante nao consegue descobrir quais IDs existem.
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    /** Status devolvido no bloqueio. Trocar para HttpStatus.NOT_FOUND se preferir esconder ate o 403. */
    public static final HttpStatus DENY_STATUS = HttpStatus.FORBIDDEN;
    public static final String DENY_MESSAGE = "Acesso negado: este recurso não pertence ao seu usuário.";

    /** Status em que o conteiner conta como "dispositivo do motorista". */
    private static final List<TripStatus> ASSIGNED = List.of(TripStatus.PLANNED, TripStatus.IN_TRANSIT);

    private final TripRepository tripRepository;
    private final SecurityEventLogger securityLog;

    /** Usuario autenticado, extraido do token validado pelo servidor. */
    public record Caller(Long id, String role) {
        public boolean isCompany() {
            return "ROLE_COMPANY".equals(role);
        }
    }

    public Caller caller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                return new Caller(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("role"));
            } catch (NumberFormatException ignored) {
                // cai no AccessDeniedException abaixo
            }
        }
        throw new AccessDeniedException("Usuário não autenticado");
    }

    // ------------------------------------------------------------------ checagens por ID

    /** Leituras, alertas, configuracao e comandos de uma viagem. */
    public void requireTrip(Long tripId) {
        Caller c = caller();
        if (c.isCompany()) {
            return; // o service responde 404 se o ID nao existir
        }
        if (tripId == null || !tripRepository.existsByIdAndDriverId(tripId, c.id())) {
            deny(c, "trip:" + tripId);
        }
    }

    /** Dados brutos, impactos e comandos de um conteiner (dispositivo IoT). */
    public void requireContainer(Long containerId) {
        Caller c = caller();
        if (c.isCompany()) {
            return;
        }
        if (containerId == null
                || !tripRepository.existsByContainerIdAndDriverIdAndStatusIn(containerId, c.id(), ASSIGNED)) {
            deny(c, "container:" + containerId);
        }
    }

    /** Operacoes de administracao da frota (cadastrar viagem, usuario, conteiner, relatorios gerais). */
    public void requireCompany(String recurso) {
        Caller c = caller();
        if (!c.isCompany()) {
            deny(c, recurso);
        }
    }

    // ------------------------------------------------------------------ filtros de listas

    public boolean isCompany() {
        return caller().isCompany();
    }

    /** IDs das viagens do motorista autenticado. */
    public Set<Long> ownTripIds() {
        return Set.copyOf(tripRepository.findIdsByDriverId(caller().id()));
    }

    /** IDs dos conteineres atualmente atribuidos ao motorista autenticado. */
    public Set<Long> ownContainerIds() {
        return Set.copyOf(tripRepository.findContainerIdsByDriverIdAndStatusIn(caller().id(), ASSIGNED));
    }

    /** Gestor recebe a lista inteira; motorista so os itens das proprias viagens. */
    public <T> List<T> onlyOwnTrips(List<T> items, Function<T, Long> tripIdOf) {
        if (isCompany()) {
            return items;
        }
        Set<Long> own = ownTripIds();
        return filter(items, tripIdOf, own);
    }

    /** Gestor recebe a lista inteira; motorista so os itens dos conteineres atribuidos a ele. */
    public <T> List<T> onlyOwnContainers(List<T> items, Function<T, Long> containerIdOf) {
        if (isCompany()) {
            return items;
        }
        return filter(items, containerIdOf, ownContainerIds());
    }

    private static <T> List<T> filter(List<T> items, Function<T, Long> idOf, Collection<Long> allowed) {
        return items.stream()
                .filter(i -> idOf.apply(i) != null && allowed.contains(idOf.apply(i)))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ bloqueio

    /** Ponto em que o controle decide bloquear: log com codigo de missao + 403. */
    private void deny(Caller c, String recurso) {
        securityLog.idorBlocked(c.id(), c.role(), recurso);
        throw new BusinessRuleException(DENY_MESSAGE, DENY_STATUS);
    }
}
