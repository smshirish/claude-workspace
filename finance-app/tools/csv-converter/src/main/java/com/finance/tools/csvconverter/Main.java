package com.finance.tools.csvconverter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main {

    private static final String AS_OF_DATE_COLUMN = "asOfDate";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java -jar csv-converter.jar <input.csv> <mapping.json> <output-dir>");
            System.exit(1);
        }

        String inputFile  = args[0];
        String mappingFile = args[1];
        String outputDirArg = args[2];

        // Read mapping as mixed String/Map values to support date-transform entries
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> rawMapping = mapper.readValue(
                new File(mappingFile),
                new TypeReference<LinkedHashMap<String, Object>>() {});

        LinkedHashMap<String, ColumnDescriptor> columnMapping = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawMapping.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                columnMapping.put(entry.getKey(), ColumnDescriptor.verbatim((String) value));
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> mapValue = (Map<String, String>) value;
                columnMapping.put(entry.getKey(),
                        ColumnDescriptor.dateTransform(mapValue.get("to"), mapValue.get("from")));
            }
        }

        String inputCsv  = Files.readString(Path.of(inputFile));
        String convertedCsv = new CsvConverter().convert(inputCsv, columnMapping);

        String asOfDate = extractAsOfDate(convertedCsv);

        Path outputDir = Path.of(outputDirArg);
        Files.createDirectories(outputDir);
        Path mappingOut = outputDir.resolve("anonym_to_real_id.json");

        Map<String, String> existingMapping = new LinkedHashMap<>();
        if (Files.exists(mappingOut)) {
            try {
                existingMapping = mapper.readValue(mappingOut.toFile(),
                        new TypeReference<LinkedHashMap<String, String>>() {});
            } catch (IOException e) {
                throw new CsvConversionException(
                        "Existing mapping file is unreadable: " + mappingOut + " (" + e.getMessage() + ")");
            }
        }

        AnonymisationResult anonymised = new Anonymiser().anonymise(convertedCsv, existingMapping);

        Path outputFile = outputDir.resolve("account_anony_" + asOfDate + ".csv");
        Files.writeString(outputFile, anonymised.csv());
        System.out.println("Written: " + outputFile);

        mapper.writerWithDefaultPrettyPrinter().writeValue(mappingOut.toFile(), anonymised.anonymToReal());
        System.out.println("Anonymisation mapping: " + mappingOut);
    }

    static String extractAsOfDate(String convertedCsv) throws CsvConversionException {
        List<String[]> rows;
        try (CSVReader reader = new CSVReader(new StringReader(convertedCsv))) {
            rows = reader.readAll();
        } catch (IOException | CsvException e) {
            throw new CsvConversionException("Failed to read converted CSV: " + e.getMessage());
        }
        int col = rows.isEmpty() ? -1 : Arrays.asList(rows.get(0)).indexOf(AS_OF_DATE_COLUMN);
        if (col < 0) {
            throw new CsvConversionException("Converted CSV has no '" + AS_OF_DATE_COLUMN + "' column");
        }
        Set<String> dates = new TreeSet<>();
        for (String[] row : rows.subList(1, rows.size())) {
            if (col < row.length && !row[col].isBlank()) {
                dates.add(row[col].trim());
            }
        }
        if (dates.size() != 1) {
            throw new CsvConversionException(dates.isEmpty()
                    ? "No " + AS_OF_DATE_COLUMN + " values found in converted CSV"
                    : "Multiple " + AS_OF_DATE_COLUMN + " values found: " + dates);
        }
        return dates.iterator().next();
    }
}
