package com.finance.tools.csvconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvConverterTest {

    private CsvConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CsvConverter();
    }

    private Map<String, String> standardMapping() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Bank", "bankName");
        m.put("AcctNum", "accountNumber");
        m.put("Type", "accountType");
        m.put("Amount", "balance");
        m.put("Curr", "currency");
        return m;
    }

    // T-1: Happy path — all mapped columns present, single data row
    @Test
    void convertsValidCsvWithCompleteMapping() throws CsvConversionException {
        String input = "Bank,AcctNum,Type,Amount,Curr\nChase,000111222,CHECKING,1500.00,USD";

        String result = converter.convert(input, standardMapping());
        String[] lines = result.trim().split("\n");

        assertEquals(2, lines.length);
        assertEquals("bankName,accountNumber,accountType,balance,currency", lines[0]);
        assertEquals("Chase,000111222,CHECKING,1500.00,USD", lines[1]);
    }

    // T-2: Extra columns in input not referenced by the mapping are ignored
    @Test
    void ignoresExtraColumnsNotInMapping() throws CsvConversionException {
        String input = "Bank,AcctNum,Type,Amount,Curr,Notes\nChase,000111222,CHECKING,1500.00,USD,primary";

        String result = converter.convert(input, standardMapping());
        String[] lines = result.trim().split("\n");

        assertEquals("bankName,accountNumber,accountType,balance,currency", lines[0]);
        assertEquals("Chase,000111222,CHECKING,1500.00,USD", lines[1]);
    }

    // T-3: Mapping references a column that does not exist in the input header
    @Test
    void throwsWhenMappedColumnMissingFromInput() {
        // AcctNum is absent from the input header
        String input = "Bank,Type,Amount,Curr\nChase,CHECKING,1500.00,USD";

        CsvConversionException ex = assertThrows(CsvConversionException.class,
                () -> converter.convert(input, standardMapping()));
        assertTrue(ex.getMessage().contains("AcctNum"),
                "Exception message should name the missing column");
    }

    // T-4: Header-only input produces header-only output (no data rows)
    @Test
    void headerOnlyInputProducesHeaderOnlyOutput() throws CsvConversionException {
        String input = "Bank,AcctNum,Type,Amount,Curr\n";

        String result = converter.convert(input, standardMapping());
        String[] lines = result.trim().split("\n");

        assertEquals(1, lines.length);
        assertEquals("bankName,accountNumber,accountType,balance,currency", lines[0]);
    }

    // T-5: Multiple data rows are all converted
    @Test
    void convertsMultipleDataRows() throws CsvConversionException {
        String input = String.join("\n",
                "Bank,AcctNum,Type,Amount,Curr",
                "Chase,000111222,CHECKING,1500.00,USD",
                "Ally,333444555,SAVINGS,800.00,USD",
                "Citi,666777888,CREDIT,-350.00,USD"
        );

        String result = converter.convert(input, standardMapping());
        String[] lines = result.trim().split("\n");

        assertEquals(4, lines.length);
        assertEquals("Chase,000111222,CHECKING,1500.00,USD", lines[1]);
        assertEquals("Ally,333444555,SAVINGS,800.00,USD", lines[2]);
        assertEquals("Citi,666777888,CREDIT,-350.00,USD", lines[3]);
    }

    // T-6: Mapping that covers only a subset of input columns — output contains only mapped columns
    @Test
    void partialMappingOutputsOnlyMappedColumns() throws CsvConversionException {
        Map<String, String> partial = new LinkedHashMap<>();
        partial.put("Bank", "bankName");
        partial.put("AcctNum", "accountNumber");
        String input = "Bank,AcctNum,Type,Amount,Curr\nChase,000111222,CHECKING,1500.00,USD";

        String result = converter.convert(input, partial);
        String[] lines = result.trim().split("\n");

        assertEquals("bankName,accountNumber", lines[0]);
        assertEquals("Chase,000111222", lines[1]);
    }
}
