package isi.dan.msclientes.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import dan.common.security.RoleAuthorizationFilter;
import dan.common.web.RegistroPeticionFilter;

@Configuration
public class SecurityConfig {

    /** Va primero: identifica cada peticion en los logs, incluidas las que se rechazan despues. */
    @Bean
    public FilterRegistrationBean<RegistroPeticionFilter> registroPeticionFilter() {
        FilterRegistrationBean<RegistroPeticionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RegistroPeticionFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RoleAuthorizationFilter> roleAuthorizationFilter() {
        FilterRegistrationBean<RoleAuthorizationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RoleAuthorizationFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
