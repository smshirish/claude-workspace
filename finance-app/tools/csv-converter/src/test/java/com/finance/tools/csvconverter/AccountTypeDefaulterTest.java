package com.finance.tools.csvconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountTypeDefaulterTest {

    private List<String> warnings;
    private AccountTypeDefaulter defaulter;

    @BeforeEach
    void setUp() {
        warnings = new ArrayList<>();
        defaulter = new AccountTypeDefaulter(warnings::add);
    }

    // D-1: All types present → unchanged, nothing logged
    @Test
    void presentTypesAreUnchanged() throws CsvConversionException {
        String csv = "bankName,accountNumber,accountType\nChase,111,CHECKING\n";

        assertEquals(csv, defaulter.apply(csv));
        assertTrue(warnings.isEmpty());
    }

    // D-2: Blank type defaults to SAVINGS and is logged with its data row number
    @Test
    void blankTypeDefaultsToSavingsAndLogs() throws CsvConversionException {
        String csv = "bankName,accountNumber,accountType,balance\n" +
                     "Chase,111,CHECKING,10\n" +
                     "Ally,222,,20\n";

        String[] lines = defaulter.apply(csv).split("\n");

        assertEquals("Chase,111,CHECKING,10", lines[1]);
        assertEquals("Ally,222,SAVINGS,20", lines[2]);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("data row 2") && warnings.get(0).contains("SAVINGS"));
    }

    // D-3: Whitespace-only type counts as blank
    @Test
    void whitespaceTypeIsTreatedAsBlank() throws CsvConversionException {
        String csv = "bankName,accountType\nChase,   \n";

        assertEquals("Chase,SAVINGS", defaulter.apply(csv).split("\n")[1]);
        assertEquals(1, warnings.size());
    }

    // D-4: Column absent → appended with SAVINGS on every row, one log per row
    @Test
    void missingColumnIsAddedWithSavings() throws CsvConversionException {
        String csv = "bankName,accountNumber\nChase,111\nAlly,222\n";

        String[] lines = defaulter.apply(csv).split("\n");

        assertEquals("bankName,accountNumber,accountType", lines[0]);
        assertEquals("Chase,111,SAVINGS", lines[1]);
        assertEquals("Ally,222,SAVINGS", lines[2]);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("column missing"));
    }

    // D-8: Column inserted at the requested position; out-of-range position appends
    @Test
    void missingColumnIsInsertedAtRequestedPosition() throws CsvConversionException {
        String csv = "bankName,accountNumber,balance\nChase,111,10\n";

        String[] lines = defaulter.apply(csv, 2).split("\n");
        assertEquals("bankName,accountNumber,accountType,balance", lines[0]);
        assertEquals("Chase,111,SAVINGS,10", lines[1]);

        String[] appended = defaulter.apply(csv, 99).split("\n");
        assertEquals("bankName,accountNumber,balance,accountType", appended[0]);
        assertEquals("Chase,111,10,SAVINGS", appended[1]);
    }

    // D-5: Header-only CSV with column absent → column added, nothing logged
    @Test
    void headerOnlyWithoutColumnGetsColumnAndNoLogs() throws CsvConversionException {
        assertEquals("bankName,accountType\n", defaulter.apply("bankName\n"));
        assertTrue(warnings.isEmpty());
    }

    // D-6: Log messages never contain account numbers
    @Test
    void logsDoNotLeakAccountNumbers() throws CsvConversionException {
        defaulter.apply("bankName,accountNumber,accountType\nChase,000111222,\n");

        assertFalse(warnings.get(0).contains("000111222"));
    }

    // D-7: Empty CSV is rejected
    @Test
    void emptyCsvThrows() {
        assertThrows(CsvConversionException.class, () -> defaulter.apply(""));
    }
}
