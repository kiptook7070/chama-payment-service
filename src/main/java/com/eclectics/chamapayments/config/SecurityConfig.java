package com.eclectics.chamapayments.config;

import com.eclectics.chamapayments.service.PermissionEvaluatorService;
import com.eclectics.chamapayments.service.impl.permissionEvaluator.ObjectAction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.security.interfaces.RSAPublicKey;

/**
 * @author Alex Maina
 * @created 06/01/2022
 */
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Lazy
    private final ApplicationContext applicationContext;
    @Lazy
    private final PermissionEvaluatorService permissionEvaluatorService;
    @Lazy
    @Autowired
    private ReactiveJwtDecoder jwtDecoder;

    @PostConstruct
    private void setAuthentication() {
        ReactiveSecurityContextHolder
                .getContext()
                .subscribe(SecurityContextHolder::setContext);
    }

    @Bean
    SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) throws Exception {
        DefaultMethodSecurityExpressionHandler defaultWebSecurityExpressionHandler = this.applicationContext.getBean(DefaultMethodSecurityExpressionHandler.class);
        defaultWebSecurityExpressionHandler.setPermissionEvaluator(permissionEvaluator());
        http
                .requestCache()
                .requestCache(NoOpServerRequestCache.getInstance())
                .and()
                .csrf().disable()
                .formLogin().disable()
                .authorizeExchange()
                .pathMatchers(
                        "/",
                        "/portal/payments/dash/accounting",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v3/api-docs",
                        "/v2/api-docs",
                        "/actuator/**",
                        "/api/v2/payment/**",
                        "/api/v2/payment/contribution/ft-callback",
                        "/api/v2/payment/ussd/account/group-info",
                        "/api/v2/payment/account/account-balances"
                )
                .permitAll()
                .anyExchange()
                .authenticated()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint((swe, e) -> Mono.fromRunnable(() -> {
                    swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                })).accessDeniedHandler((swe, e) -> Mono.fromRunnable(() -> {
                    swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                }))
                .and()
                .oauth2ResourceServer(jwtSpec -> jwtSpec.jwt().jwtDecoder(jwtDecoder).jwtAuthenticationConverter(grantedAuthoritiesExtractor()));

        return http.build();
    }


    PermissionEvaluator permissionEvaluator() {
        return new PermissionEvaluator() {
            @Override
            public boolean hasPermission(Authentication authentication, Object o, Object o1) {
                return true;
            }

            @Override
            public boolean hasPermission(Authentication authentication, Serializable targetId, String scope, Object objectAction) {
                return permissionEvaluatorService.hasPermission(authentication, targetId, scope, objectAction);
            }
        };
    }

    Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter =
                new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter
                (new GrantedAuthoritiesExtractor());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey) {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withPublicKey(rsaPublicKey).build();
        jwtDecoder.setJwtValidator(new JwtCustomValidator());
        return jwtDecoder;
    }
}
