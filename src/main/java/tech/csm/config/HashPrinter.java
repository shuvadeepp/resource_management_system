package tech.csm.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class HashPrinter implements CommandLineRunner {

    private final PasswordEncoder encoder;

    public HashPrinter(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        System.out.println("HASH = " + encoder.encode("Admin@123"));
    }
}