package com.pi.frioVale.service;

import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import com.pi.frioVale.dto.RegisterRequest;
import com.pi.frioVale.dto.UserRequest;
import com.pi.frioVale.dto.UserResponse;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll(Role role) {
        List<User> users = role == null
                ? userRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                : userRepository.findByRoleOrderByNameAsc(role);
        return users.stream().map(UserResponse::from).toList();
    }

    /** Devolve o usuário autenticado a partir do id presente no subject do token JWT. */
    @Transactional(readOnly = true)
    public UserResponse current(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessRuleException("Usuário autenticado não encontrado", HttpStatus.UNAUTHORIZED));
    }

    /** Devolve o usuário autenticado a partir do e-mail presente no token JWT. */
    @Transactional(readOnly = true)
    public UserResponse current(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessRuleException("Usuário autenticado não encontrado", HttpStatus.UNAUTHORIZED));
    }

    /** Mantido para chamadas internas que ainda não têm um usuário autenticado. */
    @Transactional(readOnly = true)
    public UserResponse current() {
        return userRepository.findFirstByRoleOrderByIdAsc(Role.ROLE_COMPANY)
                .or(() -> userRepository.findFirstByOrderByIdAsc())
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", 0L));
    }

    /** Cadastro público: o primeiro usuário vira administrador, os demais entram como motoristas. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        Role role = userRepository.count() == 0 ? Role.ROLE_COMPANY : Role.ROLE_DRIVER;
        return create(new UserRequest(
                request.name(),
                request.email(),
                request.phone(),
                role,
                request.password()));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Já existe um usuário com o e-mail '" + email + "'");
        }

        boolean senhaGerada = !StringUtils.hasText(request.password());
        String rawPassword = senhaGerada ? generatePassword() : request.password();

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .phone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null)
                .password(passwordEncoder.encode(rawPassword))
                .role(request.role())
                .build();
        User saved = userRepository.save(user);

        // Envia as credenciais (senha gerada automaticamente) para o e-mail do usuário.
        if (senhaGerada) {
            emailService.enviarCredenciais(saved.getName(), saved.getEmail(), rawPassword);
        }

        return UserResponse.from(saved);
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
