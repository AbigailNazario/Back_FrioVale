package com.pi.frioVale.security;

import com.pi.frioVale.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * [Seguranca] Registra no log cada tentativa bloqueada pelos controles, sempre com o
 * codigo de missao da equipe no inicio da mensagem.
 *
 * Nunca registra senha, codigo OTP, codigo de recuperacao, segredo ou token.
 *
 * Exemplo de linha gerada:
 *   [K7Q2M9] OTP BLOQUEADO userId=3 motivo=REPLAY metodo=POST rota=/api/auth/2fa/verify ip=127.0.0.1
 *   [K7Q2M9] IDOR BLOQUEADO userId=2 papel=ROLE_DRIVER recurso=trip:7 metodo=GET rota=/api/trips/7/telemetry ip=127.0.0.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventLogger {

    private final SecurityProperties props;

    public String missionCode() {
        return props.missionCode();
    }

    public void otpBlocked(Long userId, String motivo) {
        log.warn("[{}] OTP BLOQUEADO userId={} motivo={} {}", props.missionCode(), userId, motivo, requestInfo());
    }

    public void idorBlocked(Long userId, String role, String recurso) {
        log.warn("[{}] IDOR BLOQUEADO userId={} papel={} recurso={} {}",
                props.missionCode(), userId, role, recurso, requestInfo());
    }

    public void otpEvent(Long userId, String evento) {
        log.info("[{}] OTP {} userId={} {}", props.missionCode(), evento, userId, requestInfo());
    }

    private static String requestInfo() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return "metodo=" + req.getMethod() + " rota=" + req.getRequestURI() + " ip=" + req.getRemoteAddr();
        }
        return "origem=interna";
    }
}
