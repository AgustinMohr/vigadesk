package isi.dan.msclientes.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<RoleAuthorizationFilter> roleAuthorizationFilter() {
        FilterRegistrationBean<RoleAuthorizationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RoleAuthorizationFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
