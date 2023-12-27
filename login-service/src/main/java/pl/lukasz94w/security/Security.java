package pl.lukasz94w.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionManagementFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class Security {

    @Bean
    protected InMemoryUserDetailsManager get() {
        UserDetails user1 = User.withUsername("user1")
                .password(bcryptPasswordEncoder().encode("user1pass"))
                .build();

        UserDetails user2 = User.withUsername("user2")
                .password(bcryptPasswordEncoder().encode("user2pass"))
                .build();

//        UserDetails admin = User.withUsername("admin")
//                .password(bcryptPasswordEncoder().encode("admin123"))
//                .roles("ADMIN")
//                .build();

        return new InMemoryUserDetailsManager(Arrays.asList(user1, user2));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // here corsFilter is set which set necessary headers so the frontend app running
        // on the same machine can access application endpoints (hosted on the same machine)
        http.addFilterBefore(corsFilter(), SessionManagementFilter.class);

        // store session in repository (redis in this situation - all props set in application.properties)
        http.httpBasic((basic) -> basic.securityContextRepository(new HttpSessionSecurityContextRepository()));

        // HTTP OPTIONS method is allowed because of the fact browser likes to send preflight request (HTTP OPTIONS)
        // before reaching actual secured endpoint (to which it uses cookie/basic auth for the authorization).
        // This method should be permitted for all because only then browser will try to access protected endpoints.
        // This .cors... method was also proposed as a solution, but it didn't work for me.
        http.authorizeHttpRequests((requests) ->
                requests.requestMatchers(HttpMethod.OPTIONS, "/api/v1/auth/sign-in").permitAll()
                        .anyRequest().authenticated());

        // this was another proposition to deal with preflight request but for some reason
        // it didn't work for me...
        // http.cors(AbstractHttpConfigurer::disable);

        // in case we want roles, but it's not easy (or even possible)
        // to distinguish roles using cookies... use jwt token then instead
        // http.authorizeHttpRequests((requests) ->
        // requests.anyRequest().hasAnyRole("USER", "ADMIN"));

        // setting custom authentication entry point which overrides BasicAuthenticationEntryPoint
        // commence method (which adds WWW-Authenticate header causing browser to show off a basic auth
        // login screen when session is ended and 401 status is returned
        http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(noPopupBasicAuthenticationEntryPoint()));

        // don't remember what the settings below mean
        // http.csrf(Customizer.withDefaults())
        // http.formLogin(AbstractHttpConfigurer::disable);
        // http.cors(Customizer.withDefaults());
        // .cors(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    protected PasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    protected CorsFilter corsFilter() {
        return new CorsFilter();
    }

    @Bean
    private static AuthenticationEntryPoint noPopupBasicAuthenticationEntryPoint() {
        return new NoPopupBasicAuthenticationEntryPoint();
    }

    // required to make maximumSessions() setting works properly
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
