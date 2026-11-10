package dev.portafolio.explorer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                // Siempre a "/": si no, tras el login se abriría la última llamada a /api que falló.
                .formLogin(f -> f.defaultSuccessUrl("/", true))
                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                // En /api el front recibe 401 y redirige él mismo al login.
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // Las claves pueden venir como {bcrypt}…; sin prefijo se toman en texto plano (solo para demo).
    @Bean
    UserDetailsService users(ExplorerProperties props) {
        if (props.users().isEmpty()) {
            throw new IllegalStateException("Configura al menos un usuario en explorer.users");
        }
        var users = props.users().stream()
                .map(u -> User.withUsername(u.username())
                        .password(u.password().startsWith("{") ? u.password() : "{noop}" + u.password())
                        .roles("READER")
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }
}
