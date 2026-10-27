package tech.csm.config;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.ott.GenerateOneTimeTokenFilter;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
	private  SecretKey key;
	private Long expirationMs;
	
	public JwtService(@Value("${jwt.secret}") String secret,@Value("${jwt.expiration}") Long expirationMs)
	{
		this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
		
	}
	
	public String generateToken(String username,String role)
	{
		long now=System.currentTimeMillis();
		
		return Jwts.builder()
				.subject(username)
				.claim("role", role)
				.issuedAt(new Date(now))
				.expiration(new Date(now +expirationMs))
				.signWith(key)
				.compact();
		
	}
	
	public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                   .parseSignedClaims(token).getPayload();
    }

    public Long getExpirationMs() { return expirationMs; }

}
