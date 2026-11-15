package com.pi.frioVale.service;

import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.dto.ContainerRequest;
import com.pi.frioVale.dto.ContainerResponse;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Gerenciamento de contêineres refrigerados e seus dispositivos IoT (ThingSpeak).
 * Operações de escrita exigem perfil {@code ROLE_COMPANY}.
 */
@Service
@RequiredArgsConstructor
public class ContainerService {

    private final ContainerRepository containerRepository;
    private final TripRepository tripRepository;

    @Transactional(readOnly = true)
    public List<ContainerResponse> findAll() {
        return containerRepository.findAll().stream()
                .map(c -> ContainerResponse.from(c, isAvailable(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContainerResponse findById(Long id) {
        Container c = containerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contêiner", id));
        return ContainerResponse.from(c, isAvailable(c.getId()));
    }

    @Transactional
    public ContainerResponse create(ContainerRequest request, Jwt jwt) {
        requireCompany(jwt);

        String code = StringUtils.trimAllWhitespace(request.code());
        String channel = StringUtils.trimAllWhitespace(request.thingSpeakChannelId());

        if (containerRepository.findByCode(code).isPresent()) {
            throw new BusinessRuleException("Já existe um contêiner com o código '" + code + "'");
        }
        if (containerRepository.findByThingSpeakChannelId(channel).isPresent()) {
            throw new BusinessRuleException("Já existe um contêiner associado ao dispositivo IoT '" + channel + "'");
        }

        Container container = new Container();
        container.setCode(code);
        container.setThingSpeakChannelId(channel);
        container.setThingSpeakApiKey(null); // nunca exposto/alterado por este canal

        return ContainerResponse.from(containerRepository.save(container), true);
    }

    @Transactional
    public ContainerResponse update(Long id, ContainerRequest request, Jwt jwt) {
        requireCompany(jwt);

        Container c = containerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contêiner", id));

        String code = StringUtils.trimAllWhitespace(request.code());
        String channel = StringUtils.trimAllWhitespace(request.thingSpeakChannelId());

        if (!code.equals(c.getCode()) && containerRepository.findByCode(code).isPresent()) {
            throw new BusinessRuleException("Já existe um contêiner com o código '" + code + "'");
        }
        if (!channel.equals(c.getThingSpeakChannelId()) && containerRepository.findByThingSpeakChannelId(channel).isPresent()) {
            throw new BusinessRuleException("Já existe um contêiner associado ao dispositivo IoT '" + channel + "'");
        }

        if (isInTransit(c.getId())) {
            throw new BusinessRuleException(
                    "O contêiner '" + c.getCode() + "' está em viagem e não pode ser editado no momento.");
        }

        c.setCode(code);
        c.setThingSpeakChannelId(channel);
        return ContainerResponse.from(containerRepository.save(c), isAvailable(c.getId()));
    }

    @Transactional
    public void delete(Long id, Jwt jwt) {
        requireCompany(jwt);

        Container c = containerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contêiner", id));

        if (isInTransit(c.getId())) {
            throw new BusinessRuleException(
                    "Não é possível excluir o contêiner '" + c.getCode() + "' porque ele está em uma viagem em andamento.");
        }

        containerRepository.delete(c);
    }

    private boolean isAvailable(Long containerId) {
        return !isInTransit(containerId);
    }

    private boolean isInTransit(Long containerId) {
        return tripRepository.existsByContainerIdAndStatus(containerId, TripStatus.IN_TRANSIT);
    }

    private void requireCompany(Jwt jwt) {
        if (jwt == null || !"ROLE_COMPANY".equals(jwt.getClaimAsString("role"))) {
            throw new BusinessRuleException("Apenas administradores podem gerenciar contêineres", HttpStatus.FORBIDDEN);
        }
    }
}
