package com.finance.tools.csvconverter;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class CsvConverter {

    public String convert(String inputCsv, Map<String, String> columnMapping) throws CsvConversionException {
        List<String[]> rows;
        try (CSVReader reader = new CSVReader(new StringReader(inputCsv))) {
            rows = reader.readAll();
        } catch (IOException | CsvException e) {
            throw new CsvConversionException("Failed to parse input CSV: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new CsvConversionException("Input CSV is empty — no header found");
        }

        // Index input columns by name
        String[] inputHeader = rows.get(0);
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < inputHeader.length; i++) {
            headerIndex.put(inputHeader[i].trim(), i);
        }

        // Validate every mapping key exists in the input header
        for (String inputCol : columnMapping.keySet()) {
            if (!headerIndex.containsKey(inputCol)) {
                throw new CsvConversionException(
                        "Mapped input column not found in CSV header: '" + inputCol + "'");
            }
        }

        // Build output lines — header first, then data rows
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", columnMapping.values())).append("\n");

        for (int i = 1; i < rows.size(); i++) {
            String[] inputRow = rows.get(i);
            // Skip blank rows that may appear from a trailing newline
            if (inputRow.length == 0 || (inputRow.length == 1 && inputRow[0].trim().isEmpty())) {
                continue;
            }
            List<String> outputValues = new ArrayList<>();
            for (String inputCol : columnMapping.keySet()) {
                int idx = headerIndex.get(inputCol);
                outputValues.add(idx < inputRow.length ? inputRow[idx] : "");
            }
            sb.append(String.join(",", outputValues)).append("\n");
        }

        return sb.toString();
    }
}
