package dev.legatus.server.config;

import static org.springframework.security.config.Customizer.withDefaults;

import dev.legatus.server.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final Environment env;
    private final JHipsterProperties jHipsterProperties;
    private final org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfiguration(
        Environment env,
        JHipsterProperties jHipsterProperties,
        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter jwtAuthenticationConverter
    ) {
        this.env = env;
        this.jHipsterProperties = jHipsterProperties;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(HttpMethod.POST, "/api/authenticate").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/authenticate").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/workers/register").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/workers/register/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/worker").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/operator/login").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/bytecode/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/bytecode/**").hasAnyAuthority(AuthoritiesConstants.CLUSTER_ADMIN, AuthoritiesConstants.OPERATOR, AuthoritiesConstants.ADMIN)
                    .requestMatchers(HttpMethod.GET, "/api/v1/tasks/**").hasAnyAuthority(AuthoritiesConstants.CLUSTER_ADMIN, AuthoritiesConstants.OPERATOR, AuthoritiesConstants.AUDITOR, AuthoritiesConstants.ADMIN)
                    .requestMatchers(HttpMethod.POST, "/api/v1/tasks/**").hasAnyAuthority(AuthoritiesConstants.CLUSTER_ADMIN, AuthoritiesConstants.OPERATOR, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/v1/admin/accounts/**").hasAnyAuthority(AuthoritiesConstants.CLUSTER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/v1/admin/audit-logs/**").hasAnyAuthority(AuthoritiesConstants.CLUSTER_ADMIN, AuthoritiesConstants.AUDITOR, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/v1/admin/secrets/**").hasAnyAuthority(AuthoritiesConstants.CLUSTER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/**").authenticated()
                    .requestMatchers("/websocket/**").authenticated()
                    .requestMatchers("/v3/api-docs/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers("/management/health").permitAll()
                    .requestMatchers("/management/health/**").permitAll()
                    .requestMatchers("/management/info").permitAll()
                    .requestMatchers("/management/prometheus").permitAll()
                    .requestMatchers("/management/**").hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        if (env.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT))) {
            http.authorizeHttpRequests(authz -> authz.requestMatchers("/h2-console/**").permitAll());
        }
        return http.build();
    }
}
