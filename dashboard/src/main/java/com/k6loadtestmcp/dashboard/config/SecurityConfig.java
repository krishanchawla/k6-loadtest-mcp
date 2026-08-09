package com.k6loadtestmcp.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Two independent chains, per the ingest-vs-viewing distinction described in the README:
 *  - /api/**   : ApiTokenFilter enforces the bearer token itself; Spring Security just gets out
 *                of the way (stateless, CSRF off -- this is a machine client, not a browser form).
 *  - everything else (the Thymeleaf pages): HTTP Basic, one shared username/password from env vars,
 *    UNLESS DASHBOARD_BASIC_AUTH_PASS is left unset -- then reads are public. That's deliberate, not
 *    a fallback-open bug: it's the "public demo" posture (see README), where DASHBOARD_DEMO_TARGET_HOST
 *    on the ingest side is what actually guards the deployment, not a login. Self-hosted/private
 *    deployments set the password and get exactly today's gated behavior.
 *
 * The DaoAuthenticationProvider/AuthenticationManager below are wired explicitly (rather than
 * relying on Spring Boot's implicit UserDetailsService+PasswordEncoder auto-wiring), because that
 * implicit path was observed (during manual testing) to authenticate against
 * PasswordEncoderFactories.createDelegatingPasswordEncoder() instead of the plain BCryptPasswordEncoder
 * bean below -- DelegatingPasswordEncoder expects an "{bcrypt}"-prefixed hash, our stored hash has no
 * prefix, so matches() silently failed, rejecting correct credentials with BadCredentialsException.
 * Constructing the provider with our own encoder explicitly removes that ambiguity.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, @Value("${dashboard.api-token:}") String apiToken) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // ApiTokenFilter (added below) is what actually enforces auth on this chain; Spring
                // Security's own authorization here just needs to not block the request before the
                // filter runs.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new ApiTokenFilter(apiToken), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            @Value("${dashboard.basic-auth.password:}") String basicAuthPassword) throws Exception {
        boolean authRequired = basicAuthPassword != null && !basicAuthPassword.isBlank();
        if (authRequired) {
            http.authenticationManager(authenticationManager)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/css/**", "/js/**").permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
        } else {
            // Public demo mode -- DASHBOARD_BASIC_AUTH_PASS deliberately unset. See class javadoc.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(InMemoryUserDetailsManager userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(
            @Value("${dashboard.basic-auth.username}") String username,
            @Value("${dashboard.basic-auth.password}") String password,
            PasswordEncoder passwordEncoder) {
        UserDetails viewer = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("VIEWER")
                .build();
        return new InMemoryUserDetailsManager(viewer);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
