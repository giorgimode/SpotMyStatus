package com.giorgimode.spotmystatus;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SpotMyStatusSecurityConfiguration {

    private static final String ROLE_ADMIN = "ROLE_AUTH_PARTNER";

    @Value("${admin_users}")
    private List<String> adminUsers;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .addFilterAfter(googleFilter(), OAuth2LoginAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasAuthority(ROLE_ADMIN)
                        .anyRequest().permitAll())
                .oauth2Login(withDefaults());
        return http.build();
    }

    private Filter googleFilter() {
        return (request, response, filterChain) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof OAuth2AuthenticationToken) {
                grantUserAuthority((OAuth2AuthenticationToken) authentication);
            }
            filterChain.doFilter(request, response);
        };
    }

    private void grantUserAuthority(OAuth2AuthenticationToken token) {
        String userEmail = token.getPrincipal().getAttribute("email");
        if (isNotBlank(userEmail) && adminUsers.contains(userEmail.toLowerCase())) {
            List<GrantedAuthority> updatedAuthorities = new ArrayList<>(token.getAuthorities());
            updatedAuthorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
            OAuth2AuthenticationToken newToken = new OAuth2AuthenticationToken(token.getPrincipal(), updatedAuthorities,
                    token.getAuthorizedClientRegistrationId());
            SecurityContextHolder.getContext().setAuthentication(newToken);
        }
    }
}