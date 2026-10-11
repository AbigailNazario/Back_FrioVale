package com.pi.frioVale.service;

import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import com.pi.frioVale.dto.RegisterRequest;
import com.pi.frioVale.dto.UserResponse;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Chamado apenas pelo gestor (restrito em SecurityConfig). */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Ja existe um usuario com este e-mail");
        }
        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role() == null ? Role.ROLE_DRIVER : request.role())
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listDrivers() {
        return userRepository.findByRoleOrderByNameAsc(Role.ROLE_DRIVER).stream()
                .map(UserResponse::from).toList();
    }
}
