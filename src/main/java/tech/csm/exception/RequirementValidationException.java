package tech.csm.exception;

import java.util.Map;

/**
 * POST /api/requirements ke validation errors. Response:
 * {"error":"Validation failed","detail":"...","fields":{"project_name":"required",...}}
 */
public class RequirementValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public RequirementValidationException(String detail, Map<String, String> fields) {
        super(detail);
        this.fields = fields;
    }

    public Map<String, String> getFields() { return fields; }
}
