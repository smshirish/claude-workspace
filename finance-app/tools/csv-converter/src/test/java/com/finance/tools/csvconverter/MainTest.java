package com.finance.tools.csvconverter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Encodes FR-4 §3.6: Main.java must deserialize mixed mapping.json (String + Object entries)
 * and call the ColumnDescriptor overload of CsvConverter.convert().
 *
 * These tests fail until ColumnDescriptor, the new CsvConverter.convert() overload,
 * and the Main.java update are all implemented.
 */
class MainTest {

    @TempDir
    Path tempDir;

    // M-1: Main reads mapping.json with a date-transform entry and produces ISO-formatted output
    @Test
    void main_mixedMappingJson_transformsDateColumnToIso() throws Exception {
        Path inputCsv = tempDir.resolve("input.csv");
        Path mappingJson = tempDir.resolve("mapping.json");
        Path outputCsv = tempDir.resolve("output.csv");

        Files.writeString(inputCsv,
                "Bank,AcctNum,Type,Amount,Curr,as_of_date\n" +
                "Chase,000111222,CHECKING,1500.00,USD,21-Jun-26\n");

        // Mixed mapping.json: plain string values + one date-transform object entry
        Files.writeString(mappingJson,
                "{\n" +
                "  \"Bank\":       \"bankName\",\n" +
                "  \"AcctNum\":    \"accountNumber\",\n" +
                "  \"Type\":       \"accountType\",\n" +
                "  \"Amount\":     \"balance\",\n" +
                "  \"Curr\":       \"currency\",\n" +
                "  \"as_of_date\": { \"to\": \"asOfDate\", \"type\": \"date\", \"from\": \"dd-MMM-yy\" }\n" +
                "}\n");

        Main.main(new String[]{
                inputCsv.toString(),
                mappingJson.toString(),
                outputCsv.toString()
        });

        String output = Files.readString(outputCsv);
        String[] lines = output.trim().split("\n");

        assertEquals(2, lines.length, "Expected header + 1 data row");
        assertEquals("bankName,accountNumber,accountType,balance,currency,asOfDate", lines[0]);
        assertTrue(lines[1].endsWith(",2026-06-21"),
                "Expected data row to end with ISO date 2026-06-21 but was: " + lines[1]);
    }

    // M-2: Main propagates CsvConversionException when a date cell cannot be parsed
    @Test
    void main_mixedMappingJsonWithBadDate_throwsCsvConversionException() throws Exception {
        Path inputCsv = tempDir.resolve("input.csv");
        Path mappingJson = tempDir.resolve("mapping.json");
        Path outputCsv = tempDir.resolve("output.csv");

        Files.writeString(inputCsv,
                "Bank,AcctNum,Type,Amount,Curr,as_of_date\n" +
                "Chase,000111222,CHECKING,1500.00,USD,not-a-date\n");

        Files.writeString(mappingJson,
                "{\n" +
                "  \"Bank\":       \"bankName\",\n" +
                "  \"AcctNum\":    \"accountNumber\",\n" +
                "  \"Type\":       \"accountType\",\n" +
                "  \"Amount\":     \"balance\",\n" +
                "  \"Curr\":       \"currency\",\n" +
                "  \"as_of_date\": { \"to\": \"asOfDate\", \"type\": \"date\", \"from\": \"dd-MMM-yy\" }\n" +
                "}\n");

        // Main.main() throws — the root cause must be CsvConversionException
        Exception thrown = assertThrows(Exception.class, () ->
                Main.main(new String[]{
                        inputCsv.toString(),
                        mappingJson.toString(),
                        outputCsv.toString()
                })
        );

        Throwable cause = thrown;
        while (cause != null && !(cause instanceof CsvConversionException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause,
                "Expected CsvConversionException in the cause chain but got: " + thrown);
        assertTrue(cause.getMessage().contains("not-a-date"),
                "Exception message must name the offending value");
    }
}
