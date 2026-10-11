package com.finance.tools.csvconverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class AccountTypeDefaulter {

    static final String COLUMN = "accountType";
    static final String DEFAULT_TYPE = "SAVINGS";

    private final Consumer<String> warn;

    public AccountTypeDefaulter() {
        this(System.err::println);
    }

    public AccountTypeDefaulter(Consumer<String> warn) {
        this.warn = warn;
    }

    public String apply(String csv) throws CsvConversionException {
        return apply(csv, -1);
    }

    /** @param insertAt zero-based position for the column when it is missing; negative or too large appends */
    public String apply(String csv, int insertAt) throws CsvConversionException {
        String[] lines = csv.split("\n", -1);
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new CsvConversionException("CSV is empty — cannot default accountType");
        }

        String[] header = lines[0].split(",", -1);
        int colIdx = -1;
        for (int i = 0; i < header.length; i++) {
            if (COLUMN.equals(header[i].trim())) {
                colIdx = i;
                break;
            }
        }
        boolean columnMissing = colIdx == -1;

        int pos = insertAt < 0 || insertAt > header.length ? header.length : insertAt;

        StringBuilder sb = new StringBuilder();
        sb.append(columnMissing ? insert(header, pos, COLUMN) : lines[0]).append("\n");

        int dataRow = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            dataRow++;
            if (columnMissing) {
                String[] fields = line.split(",", -1);
                line = insert(fields, Math.min(pos, fields.length), DEFAULT_TYPE);
                warn.accept(message(dataRow, COLUMN + " column missing"));
            } else {
                String[] fields = line.split(",", -1);
                if (colIdx < fields.length && fields[colIdx].isBlank()) {
                    fields[colIdx] = DEFAULT_TYPE;
                    line = String.join(",", fields);
                    warn.accept(message(dataRow, COLUMN + " blank"));
                }
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private static String insert(String[] fields, int pos, String value) {
        List<String> list = new ArrayList<>(Arrays.asList(fields));
        list.add(pos, value);
        return String.join(",", list);
    }

    private static String message(int dataRow, String reason) {
        return "[WARN] data row " + dataRow + ": " + reason + ", defaulted to " + DEFAULT_TYPE;
    }
}
