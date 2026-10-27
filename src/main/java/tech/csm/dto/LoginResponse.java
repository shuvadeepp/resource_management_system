package tech.csm.dto;

public record LoginResponse(String token,String tokenType, Long expiresInMs, String username, String fullName,
		String role) {

}
