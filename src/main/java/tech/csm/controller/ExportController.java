package tech.csm.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tech.csm.service.ExportService;

/** .xlsx downloads (Reports screen). */
@RestController
@RequestMapping("/api")
public class ExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/requirements/{id}/matches/export")
    public ResponseEntity<byte[]> matches(@PathVariable Integer id, @RequestParam(required = false) Integer runId) {
        return file(exportService.matches(id, runId), "matches-requirement-" + id + ".xlsx");
    }

    @GetMapping("/decisions/export")
    public ResponseEntity<byte[]> decisions(@RequestParam Integer requirementId) {
        return file(exportService.decisions(requirementId), "decisions-requirement-" + requirementId + ".xlsx");
    }

    private ResponseEntity<byte[]> file(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }
}
