package tech.csm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import tech.csm.service.MatchingPersistenceService;

/**
 * Server restart par background runs mar jaate hain. Startup par aise saare
 * QUEUED/RUNNING runs FAILED mark kar do, taaki frontend ka polling hamesha khatam ho.
 */
@Component
public class StaleRunRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleRunRecovery.class);

    private final MatchingPersistenceService persistence;

    public StaleRunRecovery(MatchingPersistenceService persistence) {
        this.persistence = persistence;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = persistence.recoverInterruptedRuns();
            if (count > 0) {
                log.warn("Marked {} interrupted matching run(s) as FAILED", count);
            }
        } catch (Exception ex) {
            log.error("Could not recover interrupted matching runs", ex);
        }
    }
}
