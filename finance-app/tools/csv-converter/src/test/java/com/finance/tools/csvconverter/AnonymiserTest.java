package com.finance.tools.csvconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnonymiserTest {

    private Anonymiser anonymiser;

    @BeforeEach
    void setUp() {
        anonymiser = new Anonymiser();
    }

    // A-1: Single row — accountNumber replaced with ANON-0001, mapping entry written
    @Test
    void anonymisesSingleAccountNumber() throws CsvConversionException {
        String csv = "bankName,accountNumber,accountType,balance,currency\n" +
                     "Chase,000111222,CHECKING,1500.00,USD\n";

        AnonymisationResult result = anonymiser.anonymise(csv);

        String[] lines = result.csv().trim().split("\n");
        assertEquals("bankName,accountNumber,accountType,balance,currency", lines[0]);
        assertTrue(lines[1].contains("ANON-0001"), "Expected ANON-0001 in data row");
        assertFalse(lines[1].contains("000111222"), "Real account number must not appear in output");
        assertEquals(Map.of("ANON-0001", "000111222"), result.anonymToReal());
    }

    // A-2: Same account number on multiple rows → same ANON ID each time
    @Test
    void sameAccountNumberGetsSameAnonId() throws CsvConversionException {
        String csv = "bankName,accountNumber,accountType,balance,currency\n" +
                     "Chase,000111222,CHECKING,1500.00,USD\n" +
                     "Chase,000111222,SAVINGS,200.00,USD\n";

        AnonymisationResult result = anonymiser.anonymise(csv);

        String[] lines = result.csv().trim().split("\n");
        assertEquals(1, result.anonymToReal().size(), "Only one unique account, one mapping entry");
        assertTrue(lines[1].contains("ANON-0001"));
        assertTrue(lines[2].contains("ANON-0001"));
    }

    // A-3: Multiple distinct account numbers → sequential ANON IDs in order of first appearance
    @Test
    void distinctAccountNumbersGetSequentialAnonIds() throws CsvConversionException {
        String csv = "bankName,accountNumber,accountType,balance,currency\n" +
                     "Chase,000111222,CHECKING,1500.00,USD\n" +
                     "Ally,333444555,SAVINGS,800.00,USD\n" +
                     "Citi,666777888,CREDIT,-350.00,USD\n";

        AnonymisationResult result = anonymiser.anonymise(csv);

        assertEquals(3, result.anonymToReal().size());
        assertEquals("000111222", result.anonymToReal().get("ANON-0001"));
        assertEquals("333444555", result.anonymToReal().get("ANON-0002"));
        assertEquals("666777888", result.anonymToReal().get("ANON-0003"));
    }

    // A-4: accountNumber column absent → CsvConversionException
    @Test
    void throwsWhenAccountNumberColumnMissing() {
        String csv = "bankName,acctNum,accountType,balance,currency\n" +
                     "Chase,000111222,CHECKING,1500.00,USD\n";

        CsvConversionException ex = assertThrows(CsvConversionException.class,
                () -> anonymiser.anonymise(csv));
        assertTrue(ex.getMessage().contains("accountNumber"),
                "Exception must name the missing column");
    }

    // A-5: Header-only CSV → no mapping entries, header unchanged
    @Test
    void headerOnlyProducesEmptyMapping() throws CsvConversionException {
        String csv = "bankName,accountNumber,accountType,balance,currency\n";

        AnonymisationResult result = anonymiser.anonymise(csv);

        assertTrue(result.anonymToReal().isEmpty());
        assertTrue(result.csv().startsWith("bankName,accountNumber"));
    }

    // A-6: Main integration — anonym_to_real_id.json is written next to output, CSV is anonymised
    @Test
    void mainWritesAnonymisationMappingFileNextToOutput() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("anon-test-");
        java.nio.file.Path inputCsv = tempDir.resolve("input.csv");
        java.nio.file.Path mappingJson = tempDir.resolve("mapping.json");
        java.nio.file.Path outputCsv = tempDir.resolve("output.csv");
        java.nio.file.Path mappingOut = tempDir.resolve("anonym_to_real_id.json");

        java.nio.file.Files.writeString(inputCsv,
                "Bank,AcctNum,Type,Amount,Curr\n" +
                "Chase,000111222,CHECKING,1500.00,USD\n" +
                "Ally,333444555,SAVINGS,800.00,USD\n");
        java.nio.file.Files.writeString(mappingJson,
                "{\"Bank\":\"bankName\",\"AcctNum\":\"accountNumber\"," +
                "\"Type\":\"accountType\",\"Amount\":\"balance\",\"Curr\":\"currency\"}");

        Main.main(new String[]{inputCsv.toString(), mappingJson.toString(), outputCsv.toString()});

        assertTrue(java.nio.file.Files.exists(mappingOut), "anonym_to_real_id.json must be written");
        String mappingContent = java.nio.file.Files.readString(mappingOut);
        assertTrue(mappingContent.contains("000111222"), "Mapping must contain real account number");
        assertTrue(mappingContent.contains("ANON-0001"), "Mapping must contain anon ID");

        String output = java.nio.file.Files.readString(outputCsv);
        assertFalse(output.contains("000111222"), "Real account number must not appear in output CSV");
        assertTrue(output.contains("ANON-0001"), "Anon ID must appear in output CSV");
    }
}
