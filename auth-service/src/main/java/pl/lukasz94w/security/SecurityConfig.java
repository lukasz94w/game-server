package pl.lukasz94w.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import pl.lukasz94w.login.NoPopupBasicAuthenticationEntryPoint;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    protected InMemoryUserDetailsManager createInMemoryUsers() {
        UserDetails user1 = User.withUsername("user1")
                .password(bcryptPasswordEncoder().encode("user1pass"))
                .build();

        UserDetails user2 = User.withUsername("user2")
                .password(bcryptPasswordEncoder().encode("user2pass"))
                .build();

        UserDetails user3 = User.withUsername("user3")
                .password(bcryptPasswordEncoder().encode("user3pass"))
                .build();

        UserDetails user4 = User.withUsername("user4")
                .password(bcryptPasswordEncoder().encode("user4pass"))
                .build();

        return new InMemoryUserDetailsManager(Arrays.asList(user1, user2, user3, user4));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.httpBasic(httpBasicConfigurer -> httpBasicConfigurer.authenticationEntryPoint(noPopupBasicAuthenticationEntryPoint()));

        // Store session in repository (redis in this situation - props set in application.properties).
        http.httpBasic(httpBasicConfigurer -> httpBasicConfigurer.securityContextRepository(httpSessionSecurityContextRepository()));

        // HTTP OPTIONS method is allowed because of the fact browser tends to send preflight request (HTTP OPTIONS)
        // before reaching actual secured endpoint (to which it uses cookie/basic auth for the authorization).
        // This method should be permitted for all because only then browser will try to access protected endpoints.
        // To understand it better read about preflight requests.
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(HttpMethod.OPTIONS, "/api/v1/auth/signIn").permitAll()
                .anyRequest().authenticated());

        // Here GET method is used to log out. It's recommended to do it by POST method but for some reason it didn't work
        // for me (I was getting 403 status when trying to send request on the following url: /api/v1/auth/logout.
        // Here are the leftovers of the method triggerred by POST request: .logoutUrl("/api/v1/auth/logout").
        http.logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/api/v1/auth/signOut", "GET"))
                .logoutSuccessHandler(httpStatusReturningLogoutSuccessHandler())
        );

        http.sessionManagement(sessionManagementConfigurer -> sessionManagementConfigurer.maximumSessions(1));

        return http.build();
    }

    @Bean
    protected PasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    protected HttpSessionSecurityContextRepository httpSessionSecurityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    protected AuthenticationEntryPoint noPopupBasicAuthenticationEntryPoint() {
        return new NoPopupBasicAuthenticationEntryPoint();
    }

    // By default, 302 status is sent back suggesting there is redirection
    // (for some other website) - if that's MVC. By setting 204 status here
    // we are telling the browser not to expect any redirection.
    @Bean
    protected HttpStatusReturningLogoutSuccessHandler httpStatusReturningLogoutSuccessHandler() {
        return new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT);
    }


    // required to make maximumSessions() setting works properly
    @Bean
    protected HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
