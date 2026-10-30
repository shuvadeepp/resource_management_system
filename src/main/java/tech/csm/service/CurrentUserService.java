package tech.csm.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import tech.csm.repository.UserRepo;

/**
 * Logged-in user ka app_users.user_id (JWT subject = username).
 * Sirf request thread me kaam karta hai - background run ke liye id pehle hi
 * resolve karke pass karo.
 */
@Service
public class CurrentUserService {

    private final UserRepo userRepo;

    // username -> user_id kabhi nahi badalta; har write par DB round trip bachta hai
    private final Map<String, Integer> idByUsername = new ConcurrentHashMap<>();

    public CurrentUserService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    public Integer currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            Integer cached = idByUsername.get(auth.getName());
            if (cached != null) {
                return cached;
            }
            Integer id = userRepo.findByUserName(auth.getName()).map(u -> u.getUserId()).orElse(null);
            if (id != null) {
                idByUsername.put(auth.getName(), id);
            }
            return id;
        } catch (Exception ex) {
            return null;
        }
    }
}
