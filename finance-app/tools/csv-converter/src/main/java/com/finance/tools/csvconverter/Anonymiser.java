package com.finance.tools.csvconverter;

import java.util.LinkedHashMap;
import java.util.Map;

public class Anonymiser {

    static final String ANON_COLUMN = "accountNumber";

    public AnonymisationResult anonymise(String csv) throws CsvConversionException {
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
        Map<String, String> anonToReal = new LinkedHashMap<>();
        int counter = 1;

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
}
