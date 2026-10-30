package tech.csm.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Matches aur decisions ke .xlsx exports. Data wahi JSON endpoints wala hai,
 * isliye Excel aur screen me hamesha same numbers aate hain.
 */
@Service
public class ExportService {

    public record Column(String header, Function<Map<String, Object>, Object> value) {
    }

    private final RequirementQueryService queryService;
    private final DecisionService decisionService;

    public ExportService(RequirementQueryService queryService, DecisionService decisionService) {
        this.queryService = queryService;
        this.decisionService = decisionService;
    }

    public byte[] matches(Integer requirementId, Integer runId) {
        queryService.findRequirement(requirementId); // 404 agar requirement hi nahi hai
        List<Map<String, Object>> rows = queryService.getMatches(requirementId, runId);
        return workbook("Matches", rows, List.of(
                new Column("Rank", m -> m.get("rank_position")),
                new Column("Employee code", m -> m.get("employee_code")),
                new Column("Name", m -> m.get("resource_name")),
                new Column("Designation", m -> m.get("designation")),
                new Column("Department", m -> m.get("department")),
                new Column("Experience (years)", m -> m.get("total_experience_years")),
                new Column("Availability", m -> nested(m, "availability", "status")),
                new Column("Score", m -> m.get("overall_score")),
                new Column("Category", m -> m.get("match_category")),
                new Column("Recommendation", m -> m.get("recommendation_status")),
                new Column("Current decision", m -> nested(m, "current_decision", "decision")),
                new Column("Decision reason", m -> nested(m, "current_decision", "decision_reason")),
                new Column("Decided by", m -> nested(m, "current_decision", "decision_maker_name")),
                new Column("Explanation", m -> m.get("explanation"))));
    }

    public byte[] decisions(Integer requirementId) {
        List<Map<String, Object>> rows = decisionService.listDecisions(requirementId);
        return workbook("Decisions", rows, List.of(
                new Column("Decision id", m -> m.get("allocationDecisionId")),
                new Column("Date", m -> m.get("decisionDate")),
                new Column("Employee code", m -> m.get("employee_code")),
                new Column("Name", m -> m.get("resource_name")),
                new Column("Decision", m -> m.get("decision")),
                new Column("Reason", m -> m.get("decisionReason")),
                new Column("Decided by", m -> m.get("decision_maker_name")),
                new Column("Allocation id", m -> m.get("allocationId"))));
    }

    private byte[] workbook(String sheetName, List<Map<String, Object>> rows, List<Column> columns) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(columns.get(c).header());
                cell.setCellStyle(headerStyle);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < columns.size(); c++) {
                    Object v = columns.get(c).value().apply(rows.get(r));
                    Cell cell = row.createCell(c);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else if (v != null) {
                        cell.setCellValue(String.valueOf(v));
                    }
                }
            }
            for (int c = 0; c < columns.size(); c++) {
                sheet.autoSizeColumn(c);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not build Excel file", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object nested(Map<String, Object> m, String key, String field) {
        Object inner = m.get(key);
        return inner instanceof Map<?, ?> map ? ((Map<String, Object>) map).get(field) : null;
    }
}
