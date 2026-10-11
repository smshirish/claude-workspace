package com.finance.tools.csvconverter;

import java.util.LinkedHashMap;
import java.util.Map;

public class Anonymiser {

    static final String ANON_COLUMN = "accountNumber";

    private static final String ANON_PREFIX = "ANON-";

    public AnonymisationResult anonymise(String csv) throws CsvConversionException {
        return anonymise(csv, Map.of());
    }

    public AnonymisationResult anonymise(String csv, Map<String, String> existingAnonToReal)
            throws CsvConversionException {
        String[] lines = csv.split("\n", -1);
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new CsvConversionException("CSV is empty — cannot anonymise");
        }

        String[] headers = lines[0].split(",", -1);
        int colIdx = -1;
        for (int i = 0; i < headers.length; i++) {
            if (ANON_COLUMN.equals(headers[i].trim())) {
                colIdx = i;
                break;
            }
        }
        if (colIdx == -1) {
            throw new CsvConversionException(
                    "Anonymisation column not found in output CSV header: " + ANON_COLUMN);
        }

        Map<String, String> realToAnon = new LinkedHashMap<>();
        Map<String, String> anonToReal = new LinkedHashMap<>(existingAnonToReal);
        int counter = 1;
        for (Map.Entry<String, String> e : existingAnonToReal.entrySet()) {
            realToAnon.put(e.getValue(), e.getKey());
            counter = Math.max(counter, parseSuffix(e.getKey()) + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(lines[0]).append("\n");

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String[] fields = line.split(",", -1);
            if (colIdx < fields.length) {
                String realId = fields[colIdx];
                if (!realToAnon.containsKey(realId)) {
                    String anonId = String.format("ANON-%04d", counter++);
                    realToAnon.put(realId, anonId);
                    anonToReal.put(anonId, realId);
                }
                fields[colIdx] = realToAnon.get(realId);
            }
            sb.append(String.join(",", fields)).append("\n");
        }

        return new AnonymisationResult(sb.toString(), anonToReal);
    }

    private static int parseSuffix(String anonId) throws CsvConversionException {
        try {
            if (anonId.startsWith(ANON_PREFIX)) {
                return Integer.parseInt(anonId.substring(ANON_PREFIX.length()));
            }
        } catch (NumberFormatException ignored) {
            // falls through to the exception below
        }
        throw new CsvConversionException("Invalid anonymisation ID in existing mapping: '" + anonId + "'");
    }
}
