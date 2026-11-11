package tech.csm.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of("http://localhost:4200"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }

   
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // browser preflight requests carry no Authorization header - must be open
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/auth/**", "/error").permitAll()

                // Tagged person ko release karna (allocation khatam / approval withdraw) - sirf admin
                .requestMatchers(HttpMethod.POST, "/api/tagged-people/release").hasRole("ADMIN")

                // Learning recommendation ka review (FR-GAP-06): sirf L&K side + admin.
                // PUT ka pehle koi rule nahi tha - ab ye endpoint role se bandha hai.
                .requestMatchers(HttpMethod.PUT, "/api/learning-recommendations/*/review")
                    .hasAnyRole("ADMIN", "LK_ADMIN", "SKILL_REVIEWER")

                // Generate: L&K reviewer bhi chala sake (pehle sirf USER/ADMIN/PROJECT_MANAGER ke POST allowed the)
                .requestMatchers(HttpMethod.POST, "/api/matching-results/*/learning-recommendations/generate")
                    .hasAnyRole("USER", "ADMIN", "PROJECT_MANAGER", "LK_ADMIN", "SKILL_REVIEWER")

                // L&K training repository: banana, badalna aur approve karna sirf L&K admin + admin
                .requestMatchers(HttpMethod.POST, "/api/learning-resources").hasAnyRole("ADMIN", "LK_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/learning-resources/**").hasAnyRole("ADMIN", "LK_ADMIN")

                // Evidence record karna (FR-GAP-07): skill validate karne wale roles
                .requestMatchers(HttpMethod.POST, "/api/learning-recommendations/*/evidence")
                    .hasAnyRole("ADMIN", "LK_ADMIN", "SKILL_REVIEWER", "RESOURCE_MANAGER")

                // Rematch (FR-GAP-08): requirement owner side + L&K jo training ke baad dobara match chalana chahe
                .requestMatchers(HttpMethod.POST, "/api/requirements/*/rematch")
                    .hasAnyRole("USER", "ADMIN", "PROJECT_MANAGER", "RESOURCE_MANAGER", "LK_ADMIN")

                // Admin tools (skill_gaps backfill)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("USER", "ADMIN","PROJECT_MANAGER","LK_ADMIN",
                		"RESOURCE_MANAGER","SKILL_REVIEWER")
                .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("USER", "ADMIN","PROJECT_MANAGER")

                // Abhi ke liye koi role restriction nahi - bas logged-in hona chahiye.
                // Role-based rules baad me per-endpoint add karenge jab zaroorat pade.
                .requestMatchers("/api/**").authenticated()

                .anyRequest().authenticated())
            .exceptionHandling(e -> e
            	    .authenticationEntryPoint((request, response, authException) -> {
            	        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            	        response.setContentType("application/json");
            	        response.getWriter().write(
            	            "{\"message\":\"You are not authorized. Please login first.\"}"
            	        );
            	    })
            	    .accessDeniedHandler((request, response, accessDeniedException) -> {
            	        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            	        response.setContentType("application/json");
            	        response.getWriter().write(
            	            "{\"message\":\"You do not have permission to do this.\"}"
            	        );
            	    })
            	)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}