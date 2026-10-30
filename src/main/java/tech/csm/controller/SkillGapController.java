package tech.csm.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tech.csm.dto.ReviewRequest;
import tech.csm.service.SkillGapService;

/**
 * Skill gaps (Java se, deterministic) aur AI learning recommendations ka review.
 */
@RestController
@RequestMapping("/api")
public class SkillGapController {

    private final SkillGapService skillGapService;

    public SkillGapController(SkillGapService skillGapService) {
        this.skillGapService = skillGapService;
    }

    @GetMapping("/matching-results/{id}/skill-gaps")
    public ResponseEntity<?> forResult(@PathVariable Integer id) {
        return ResponseEntity.ok(skillGapService.forResult(id));
    }

    // Latest finished run ke har match ke gaps (Skill Growth screen)
    @GetMapping("/requirements/{id}/skill-gaps")
    public ResponseEntity<?> forRequirement(@PathVariable Integer id) {
        return ResponseEntity.ok(skillGapService.forRequirement(id));
    }

    @PutMapping("/learning-recommendations/{id}/review")
    public ResponseEntity<?> review(@PathVariable Integer id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(skillGapService.review(id, request.reviewStatus(), request.reviewComments()));
    }

    // Jin gaps ki recommendation missing hai unke liye ek AI call. Fail ho to 200 + "note", 500 nahi.
    @PostMapping("/matching-results/{id}/learning-recommendations/generate")
    public ResponseEntity<?> generate(@PathVariable Integer id) {
        return ResponseEntity.ok(skillGapService.generate(id));
    }
}
