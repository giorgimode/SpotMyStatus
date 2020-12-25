package com.giorgimode.spotmystatus;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SpotMyStatusSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private static final String ROLE_ADMIN = "ROLE_AUTH_PARTNER";

    @Value("${admin_users}")
    private List<String> adminUsers;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .addFilterAfter(googleFilter(), OAuth2LoginAuthenticationFilter.class)
            .authorizeRequests()
            .antMatchers("/admin/**")
            .hasAuthority(ROLE_ADMIN)
            .and()
            .authorizeRequests()
            .anyRequest()
            .permitAll()
            .and()
            .oauth2Login();
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