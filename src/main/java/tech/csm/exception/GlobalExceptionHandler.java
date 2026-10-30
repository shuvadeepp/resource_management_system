package tech.csm.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", "Validation failed");
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(SkillNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSkillNotFound(SkillNotFoundException ex) {
        // Sirf POST /api/requirements se aata hai -> naya format; missing_skills purane clients ke liye bhi
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("required_skills", "not in the skill master: " + String.join(", ", ex.getMissingSkills()));
        Map<String, Object> body = validationBody("Some skills are not in the skill master", fields);
        body.put("missing_skills", ex.getMissingSkills());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // POST /api/requirements: mandatory fields / project_id
    @ExceptionHandler(RequirementValidationException.class)
    public ResponseEntity<Map<String, Object>> handleRequirementValidation(RequirementValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(validationBody(ex.getMessage(), ex.getFields()));
    }

    private static Map<String, Object> validationBody(String detail, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Validation failed");
        body.put("detail", detail);
        body.put("fields", fields);
        return body;
    }

    // id se record na mila (findById().orElseThrow(), ya aisa hi kuch)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage() != null ? ex.getMessage() : "Record not found");
    }

    // 400 / 409 / 503 jaise specific errors, readable message ke saath
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return error(ex.getStatus(), ex.getMessage());
    }

    // Body ka JSON hi galat hai (missing comma, galat date format, ...)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex,
                                                                HttpServletRequest request) {
        if ("POST".equals(request.getMethod()) && request.getRequestURI().endsWith("/api/requirements")) {
            // Is endpoint par bhi wahi format, aur jo field toota uska naam
            String field = failingField(ex);
            Map<String, String> fields = new LinkedHashMap<>();
            if (field == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(validationBody("Request body is not valid JSON", fields));
            }
            fields.put(field, switch (field) {
                case "joining_date" -> "must be a date in yyyy-MM-dd format";
                case "minimum_experience_years" -> "must be a number";
                case "required_skills" -> "required: send a list of skills";
                default -> "has a wrong type";
            });
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(validationBody("Invalid value for " + field, fields));
        }
        return error(HttpStatus.BAD_REQUEST, "Request body is not valid JSON or has a wrongly typed field");
    }

    /** Jackson parse error ka JSON path, jaise "joining_date" ya "required_skills[0].minimum_proficiency". */
    private static String failingField(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof JacksonException je) {
                StringBuilder sb = new StringBuilder();
                for (JacksonException.Reference ref : je.getPath()) {
                    if (ref.getPropertyName() != null) {
                        if (sb.length() > 0) {
                            sb.append('.');
                        }
                        sb.append(ref.getPropertyName());
                    } else if (ref.getIndex() >= 0) {
                        sb.append('[').append(ref.getIndex()).append(']');
                    }
                }
                return sb.length() == 0 ? null : sb.toString();
            }
        }
        return null;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "Missing query parameter: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    // baaki sab kuch - ab ek hi consistent JSON shape me 500 aayega
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", "Something went wrong: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}