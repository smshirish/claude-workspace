package com.finance.tools.csvconverter;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class CsvConverter {

    public String convert(String inputCsv, Map<String, String> columnMapping) throws CsvConversionException {
        LinkedHashMap<String, ColumnDescriptor> descriptors = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : columnMapping.entrySet()) {
            descriptors.put(entry.getKey(), ColumnDescriptor.verbatim(entry.getValue()));
        }
        return convertInternal(inputCsv, descriptors);
    }

    public String convert(String inputCsv, LinkedHashMap<String, ColumnDescriptor> mapping) throws CsvConversionException {
        return convertInternal(inputCsv, mapping);
    }

    private String convertInternal(String inputCsv, LinkedHashMap<String, ColumnDescriptor> mapping) throws CsvConversionException {
        List<String[]> rows;
        try (CSVReader reader = new CSVReader(new StringReader(inputCsv))) {
            rows = reader.readAll();
        } catch (IOException | CsvException e) {
            throw new CsvConversionException("Failed to parse input CSV: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new CsvConversionException("Input CSV is empty — no header found");
        }

        String[] inputHeader = rows.get(0);
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < inputHeader.length; i++) {
            headerIndex.put(inputHeader[i].trim(), i);
        }

        for (String inputCol : mapping.keySet()) {
            if (!headerIndex.containsKey(inputCol)) {
                throw new CsvConversionException(
                        "Mapped input column not found in CSV header: '" + inputCol + "'");
            }
        }

        StringBuilder sb = new StringBuilder();
        List<String> outputCols = new ArrayList<>();
        for (ColumnDescriptor desc : mapping.values()) {
            outputCols.add(desc.outputColumn());
        }
        sb.append(String.join(",", outputCols)).append("\n");

        for (int i = 1; i < rows.size(); i++) {
            String[] inputRow = rows.get(i);
            if (inputRow.length == 0 || (inputRow.length == 1 && inputRow[0].trim().isEmpty())) {
                continue;
            }
            int rowIndex = i;
            List<String> outputValues = new ArrayList<>();
            for (Map.Entry<String, ColumnDescriptor> entry : mapping.entrySet()) {
                String inputCol = entry.getKey();
                ColumnDescriptor descriptor = entry.getValue();
                int idx = headerIndex.get(inputCol);
                String rawValue = idx < inputRow.length ? inputRow[idx] : "";

                if (descriptor.isDateTransform()) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                            descriptor.dateFromPattern(), Locale.ENGLISH);
                    try {
                        LocalDate date = LocalDate.parse(rawValue, formatter);
                        outputValues.add(date.toString());
                    } catch (DateTimeParseException e) {
                        throw new CsvConversionException(
                                "Column '" + inputCol + "': unparseable date '" + rawValue
                                        + "' on data row " + rowIndex
                                        + " (expected: " + descriptor.dateFromPattern() + ")");
                    }
                } else {
                    outputValues.add(rawValue);
                }
            }
            sb.append(String.join(",", outputValues)).append("\n");
        }

        return sb.toString();
    }
}
