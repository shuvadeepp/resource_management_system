package tech.csm.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.csm.model.User;

public interface UserRepo extends JpaRepository<User, Integer> {
	
	Optional<User> findByUserName(String username);

}
