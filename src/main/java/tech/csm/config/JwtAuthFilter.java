package tech.csm.config;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.autoconfigure.web.WebProperties.Resources.Chain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
	
	private JwtService jwtservice;
	
	public JwtAuthFilter(JwtService jwtservice)
	{
		this.jwtservice = jwtservice;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
	                                FilterChain chain) throws ServletException, IOException {
	    String header = request.getHeader("Authorization");
	    if (header != null && header.startsWith("Bearer ")) {
	        try {
	            Claims c = jwtservice.parse(header.substring(7));
	            String role = c.get("role", String.class);
	            var auth = new UsernamePasswordAuthenticationToken(
	                    c.getSubject(), null,
	                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
	            SecurityContextHolder.getContext().setAuthentication(auth);
	        } catch (Exception e) {
	            SecurityContextHolder.clearContext();
	        }
	    }
	    chain.doFilter(request, response);   // always continue
	}

}
