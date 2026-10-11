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

    private static final String INPUT_HEADER = "Bank,AcctNum,Type,Amount,Curr,as_of_date\n";

    private static final String MAPPING_JSON =
            "{\n" +
            "  \"Bank\":       \"bankName\",\n" +
            "  \"AcctNum\":    \"accountNumber\",\n" +
            "  \"Type\":       \"accountType\",\n" +
            "  \"Amount\":     \"balance\",\n" +
            "  \"Curr\":       \"currency\",\n" +
            "  \"as_of_date\": { \"to\": \"asOfDate\", \"type\": \"date\", \"from\": \"dd-MMM-yy\" }\n" +
            "}\n";

    private Path run(String inputBody, Path outputDir) throws Exception {
        Path in = Files.createTempFile(tempDir, "input", ".csv");
        Path mapping = tempDir.resolve("mapping.json");
        Files.writeString(in, INPUT_HEADER + inputBody);
        Files.writeString(mapping, MAPPING_JSON);
        Main.main(new String[]{in.toString(), mapping.toString(), outputDir.toString()});
        return outputDir;
    }

    // M-11: Blank accountType in the input is written as SAVINGS
    @Test
    void main_blankAccountType_defaultsToSavings() throws Exception {
        Path out = run("Chase,000111222,,1500.00,USD,21-Jun-26\n", tempDir.resolve("o"));
        String row = Files.readString(out.resolve("account_anony_2026-06-21.csv")).split("\n")[1];
        assertEquals("Chase,ANON-0001,SAVINGS,1500.00,USD,2026-06-21", row);
    }

    // M-12: accountType column absent from the input file → added as SAVINGS at its mapping.json position
    @Test
    void main_accountTypeColumnAbsentFromInput_addedAsSavings() throws Exception {
        Path in = tempDir.resolve("in.csv");
        Path mapping = tempDir.resolve("m.json");
        Files.writeString(in, "Bank,AcctNum,Amount,Curr,as_of_date\nChase,000111222,1500.00,USD,21-Jun-26\n");
        Files.writeString(mapping, MAPPING_JSON);
        Path out = tempDir.resolve("o");

        Main.main(new String[]{in.toString(), mapping.toString(), out.toString()});

        String[] lines = Files.readString(out.resolve("account_anony_2026-06-21.csv")).split("\n");
        assertEquals("bankName,accountNumber,accountType,balance,currency,asOfDate", lines[0]);
        assertEquals("Chase,ANON-0001,SAVINGS,1500.00,USD,2026-06-21", lines[1]);
    }

    // M-13: Position follows mapping.json order, whatever it is (here accountType is mapped first)
    @Test
    void main_missingAccountTypePositionFollowsMappingOrder() throws Exception {
        Path in = tempDir.resolve("in.csv");
        Path mapping = tempDir.resolve("m.json");
        Files.writeString(in, "Bank,AcctNum,as_of_date\nChase,000111222,21-Jun-26\n");
        Files.writeString(mapping,
                "{\"Type\":\"accountType\",\"Bank\":\"bankName\",\"AcctNum\":\"accountNumber\"," +
                "\"as_of_date\":{\"to\":\"asOfDate\",\"type\":\"date\",\"from\":\"dd-MMM-yy\"}}");
        Path out = tempDir.resolve("o");

        Main.main(new String[]{in.toString(), mapping.toString(), out.toString()});

        String[] lines = Files.readString(out.resolve("account_anony_2026-06-21.csv")).split("\n");
        assertEquals("accountType,bankName,accountNumber,asOfDate", lines[0]);
        assertEquals("SAVINGS,Chase,ANON-0001,2026-06-21", lines[1]);
    }

    // M-3: Output file name uses the ISO asOfDate
    @Test
    void main_outputFileNamedWithIsoAsOfDate() throws Exception {
        Path out = run("Chase,000111222,CHECKING,1500.00,USD,05-Mar-25\n", tempDir.resolve("o"));
        assertTrue(Files.exists(out.resolve("account_anony_2025-03-05.csv")));
    }

    // M-4: Missing output directory is created
    @Test
    void main_createsMissingOutputDirectory() throws Exception {
        Path out = run("Chase,000111222,CHECKING,1500.00,USD,21-Jun-26\n", tempDir.resolve("a/b/c"));
        assertTrue(Files.isDirectory(out));
    }

    // M-5: Several distinct asOfDate values → exception naming the dates, no CSV written
    @Test
    void main_multipleAsOfDates_throws() {
        Path out = tempDir.resolve("o");
        Exception e = assertThrows(Exception.class, () -> run(
                "Chase,000111222,CHECKING,1500.00,USD,21-Jun-26\n" +
                "Ally,333444555,SAVINGS,800.00,USD,22-Jun-26\n", out));
        assertTrue(e.getMessage().contains("2026-06-21") && e.getMessage().contains("2026-06-22"));
        assertFalse(Files.exists(out.resolve("anonym_to_real_id.json")));
    }

    // M-6: Mapping without an asOfDate output column → exception
    @Test
    void main_noAsOfDateColumn_throws() throws Exception {
        Path in = tempDir.resolve("in.csv");
        Path mapping = tempDir.resolve("m.json");
        Files.writeString(in, "Bank,AcctNum\nChase,000111222\n");
        Files.writeString(mapping, "{\"Bank\":\"bankName\",\"AcctNum\":\"accountNumber\"}");
        Exception e = assertThrows(Exception.class, () -> Main.main(
                new String[]{in.toString(), mapping.toString(), tempDir.resolve("o").toString()}));
        assertTrue(e.getMessage().contains("asOfDate"));
    }

    // M-7: Header-only input (no asOfDate values) → exception
    @Test
    void main_noAsOfDateValues_throws() {
        Exception e = assertThrows(Exception.class, () -> run("", tempDir.resolve("o")));
        assertTrue(e.getMessage().contains("asOfDate"));
    }

    // M-8: Repeated rows sharing one date produce a single file
    @Test
    void main_rowsSharingOneDate_succeed() throws Exception {
        Path out = run("Chase,000111222,CHECKING,1500.00,USD,21-Jun-26\n" +
                       "Ally,333444555,SAVINGS,800.00,USD,21-Jun-26\n", tempDir.resolve("o"));
        assertTrue(Files.exists(out.resolve("account_anony_2026-06-21.csv")));
    }

    // M-9: Second run keeps first run's IDs and appends new ones in the same mapping file
    @Test
    void main_secondRunMergesMapping() throws Exception {
        Path out = tempDir.resolve("o");
        run("Chase,000111222,CHECKING,1500.00,USD,21-Jun-26\n" +
            "Ally,333444555,SAVINGS,800.00,USD,21-Jun-26\n", out);
        run("Ally,333444555,SAVINGS,900.00,USD,22-Jun-26\n" +
            "Citi,666777888,CREDIT,-350.00,USD,22-Jun-26\n", out);

        String second = Files.readString(out.resolve("account_anony_2026-06-22.csv"));
        assertTrue(second.contains("Ally,ANON-0002"), "Known account keeps ANON-0002");
        assertTrue(second.contains("Citi,ANON-0003"), "New account gets next ID");

        String mapping = Files.readString(out.resolve("anonym_to_real_id.json"));
        assertTrue(mapping.contains("000111222") && mapping.contains("333444555")
                && mapping.contains("666777888"), "Mapping accumulates entries from both runs");
        assertTrue(Files.exists(out.resolve("account_anony_2026-06-21.csv")));
    }

    // M-10: Corrupt existing mapping fails and is left untouched
    @Test
    void main_corruptExistingMapping_failsWithoutOverwrite() throws Exception {
        Path out = Files.createDirectories(tempDir.resolve("o"));
        Path existing = out.resolve("anonym_to_real_id.json");
        Files.writeString(existing, "{not json");

        assertThrows(Exception.class,
                () -> run("Chase,000111222,CHECKING,1500.00,USD,21-Jun-26\n", out));
        assertEquals("{not json", Files.readString(existing));
    }

    // M-1: Main reads mapping.json with a date-transform entry and produces ISO-formatted output
    @Test
    void main_mixedMappingJson_transformsDateColumnToIso() throws Exception {
        Path inputCsv = tempDir.resolve("input.csv");
        Path mappingJson = tempDir.resolve("mapping.json");
        Path outputDir = tempDir.resolve("out");

        Files.writeString(inputCsv,
                "Bank,AcctNum,Type,Amount,Curr,as_of_date\n" +
                "Chase,000111222,CHECKING,1500.00,USD,21-Jun-26\n");

        // Mixed mapping.json: plain string values + one date-transform object entry
        Files.writeString(mappingJson, MAPPING_JSON);

        Main.main(new String[]{
                inputCsv.toString(),
                mappingJson.toString(),
                outputDir.toString()
        });

        String output = Files.readString(outputDir.resolve("account_anony_2026-06-21.csv"));
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

        Files.writeString(inputCsv,
                "Bank,AcctNum,Type,Amount,Curr,as_of_date\n" +
                "Chase,000111222,CHECKING,1500.00,USD,not-a-date\n");

        Files.writeString(mappingJson, MAPPING_JSON);

        // Main.main() throws — the root cause must be CsvConversionException
        Exception thrown = assertThrows(Exception.class, () ->
                Main.main(new String[]{
                        inputCsv.toString(),
                        mappingJson.toString(),
                        tempDir.toString()
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
