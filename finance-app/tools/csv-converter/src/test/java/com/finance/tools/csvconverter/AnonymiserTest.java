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

    private static final String HEADER = "bankName,accountNumber,accountType,balance,currency\n";

    // A-7: Account already in the existing mapping reuses its ANON ID
    @Test
    void knownAccountNumberReusesExistingAnonId() throws CsvConversionException {
        Map<String, String> existing = Map.of("ANON-0007", "000111222");

        AnonymisationResult result = anonymiser.anonymise(
                HEADER + "Chase,000111222,CHECKING,1500.00,USD\n", existing);

        assertTrue(result.csv().split("\n")[1].contains("ANON-0007"));
        assertEquals(Map.of("ANON-0007", "000111222"), result.anonymToReal());
    }

    // A-8: New account gets highest existing ID + 1, not ANON-0001
    @Test
    void newAccountNumberContinuesAfterHighestExistingId() throws CsvConversionException {
        Map<String, String> existing = Map.of("ANON-0001", "000111222", "ANON-0002", "333444555");

        AnonymisationResult result = anonymiser.anonymise(
                HEADER + "Citi,666777888,CREDIT,-350.00,USD\n", existing);

        assertTrue(result.csv().split("\n")[1].contains("ANON-0003"));
        assertEquals("666777888", result.anonymToReal().get("ANON-0003"));
    }

    // A-9: Existing entries absent from the current CSV are preserved
    @Test
    void existingEntriesNotInCurrentCsvArePreserved() throws CsvConversionException {
        Map<String, String> existing = Map.of("ANON-0001", "000111222", "ANON-0002", "333444555");

        AnonymisationResult result = anonymiser.anonymise(
                HEADER + "Chase,000111222,CHECKING,1500.00,USD\n", existing);

        assertEquals(existing, result.anonymToReal());
    }

    // A-10: Gaps in existing IDs are not filled — next ID follows the maximum
    @Test
    void gapInExistingIdsIsNotFilled() throws CsvConversionException {
        Map<String, String> existing = Map.of("ANON-0001", "000111222", "ANON-0005", "333444555");

        AnonymisationResult result = anonymiser.anonymise(
                HEADER + "Citi,666777888,CREDIT,-350.00,USD\n", existing);

        assertEquals("666777888", result.anonymToReal().get("ANON-0006"));
        assertEquals(3, result.anonymToReal().size());
    }

    // A-11: Empty existing mapping behaves like a first run
    @Test
    void emptyExistingMappingBehavesLikeFirstRun() throws CsvConversionException {
        String csv = HEADER + "Chase,000111222,CHECKING,1500.00,USD\n";

        assertEquals(anonymiser.anonymise(csv).anonymToReal(),
                anonymiser.anonymise(csv, Map.of()).anonymToReal());
    }

    // A-12: Malformed ID in the existing mapping is rejected
    @Test
    void malformedExistingIdThrows() {
        assertThrows(CsvConversionException.class, () -> anonymiser.anonymise(
                HEADER + "Chase,000111222,CHECKING,1500.00,USD\n", Map.of("BAD-ID", "000111222")));
    }

    // A-6: Main integration — anonym_to_real_id.json is written to the output dir, CSV is anonymised
    @Test
    void mainWritesAnonymisationMappingFileNextToOutput() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("anon-test-");
        java.nio.file.Path inputCsv = tempDir.resolve("input.csv");
        java.nio.file.Path mappingJson = tempDir.resolve("mapping.json");
        java.nio.file.Path outputCsv = tempDir.resolve("account_anony_2026-06-21.csv");
        java.nio.file.Path mappingOut = tempDir.resolve("anonym_to_real_id.json");

        java.nio.file.Files.writeString(inputCsv,
                "Bank,AcctNum,Type,Amount,Curr,as_of_date\n" +
                "Chase,000111222,CHECKING,1500.00,USD,21-Jun-26\n" +
                "Ally,333444555,SAVINGS,800.00,USD,21-Jun-26\n");
        java.nio.file.Files.writeString(mappingJson,
                "{\"Bank\":\"bankName\",\"AcctNum\":\"accountNumber\"," +
                "\"Type\":\"accountType\",\"Amount\":\"balance\",\"Curr\":\"currency\"," +
                "\"as_of_date\":{\"to\":\"asOfDate\",\"type\":\"date\",\"from\":\"dd-MMM-yy\"}}");

        Main.main(new String[]{inputCsv.toString(), mappingJson.toString(), tempDir.toString()});

        assertTrue(java.nio.file.Files.exists(mappingOut), "anonym_to_real_id.json must be written");
        String mappingContent = java.nio.file.Files.readString(mappingOut);
        assertTrue(mappingContent.contains("000111222"), "Mapping must contain real account number");
        assertTrue(mappingContent.contains("ANON-0001"), "Mapping must contain anon ID");

        String output = java.nio.file.Files.readString(outputCsv);
        assertFalse(output.contains("000111222"), "Real account number must not appear in output CSV");
        assertTrue(output.contains("ANON-0001"), "Anon ID must appear in output CSV");
    }
}
