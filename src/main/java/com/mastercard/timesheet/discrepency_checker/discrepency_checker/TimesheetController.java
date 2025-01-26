package com.mastercard.timesheet.discrepency_checker.discrepency_checker;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@RestController
@RequestMapping("/api/timesheets")
public class TimesheetController {

    @PostMapping("/compare")
    public Map<String, Object> compareTimesheets(
            @RequestParam("prismFile") MultipartFile prismFile,
            @RequestParam("beelineFile") MultipartFile beelineFile,
            @RequestParam("mappingFile") MultipartFile mappingFile
    ) throws Exception {
        // Load the employee mapping
        Map<String, String> employeeMapping = loadEmployeeMapping(mappingFile.getInputStream());

        // Parse the timesheets
        List<Map<String, String>> prismData = parsePrismTimesheet(prismFile.getInputStream());
        List<Map<String, String>> beelineData = parseBeelineTimesheet(beelineFile.getInputStream());

        // Find discrepancies
        List<Map<String, String>> discrepancies = findDiscrepancies(prismData, beelineData, employeeMapping);

        return Map.of("status", "success", "discrepancies", discrepancies);
    }

    private Map<String, String> loadEmployeeMapping(InputStream inputStream) throws Exception {
        Map<String, String> mapping = new HashMap<>();
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // Skip header row
            String fulcrumId = row.getCell(0).getStringCellValue().trim();
            String masterCardId = row.getCell(1).getStringCellValue().trim();
            mapping.put(fulcrumId, masterCardId);
        }

        return mapping;
    }

    private List<Map<String, String>> parsePrismTimesheet(InputStream inputStream) throws Exception {
        List<Map<String, String>> data = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // Skip header row
            Map<String, String> record = new HashMap<>();
            record.put("UserEmployeeID", row.getCell(5).getStringCellValue()); // Fulcrum ID
            record.put("TypeOfHours", row.getCell(17).getStringCellValue()); // Type of hours
            record.put("TotalHours", row.getCell(18).getStringCellValue()); // Total hours
            data.add(record);
        }

        return data;
    }

    private List<Map<String, String>> parseBeelineTimesheet(InputStream inputStream) throws Exception {
        List<Map<String, String>> data = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // Skip header row
            Map<String, String> record = new HashMap<>();
            record.put("MasterCardID", row.getCell(10).getStringCellValue()); // MasterCard ID
            record.put("Units", row.getCell(6).getStringCellValue()); // Units worked
            data.add(record);
        }

        return data;
    }

    private List<Map<String, String>> findDiscrepancies(
            List<Map<String, String>> prismData,
            List<Map<String, String>> beelineData,
            Map<String, String> employeeMapping
    ) {
        List<Map<String, String>> discrepancies = new ArrayList<>();

        for (Map<String, String> prismRecord : prismData) {
            String fulcrumId = prismRecord.get("UserEmployeeID");
            String masterCardId = employeeMapping.get(fulcrumId);

            if (masterCardId == null) {
                discrepancies.add(Map.of(
                        "fulcrumId", fulcrumId,
                        "error", "MasterCard ID mapping not found"
                ));
                continue;
            }

            boolean matched = beelineData.stream().anyMatch(beelineRecord ->
                    beelineRecord.get("MasterCardID").equals(masterCardId) &&
                            prismRecord.get("TotalHours").equals(beelineRecord.get("Units"))
            );

            if (!matched) {
                discrepancies.add(Map.of(
                        "fulcrumId", fulcrumId,
                        "masterCardId", masterCardId,
                        "error", "Discrepancy in hours"
                ));
            }
        }

        return discrepancies;
    }
}
