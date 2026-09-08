package com.finance.tools.csvconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    // T-7 (new): date-transform mapping converts "21-Jun-26" using "dd-MMM-yy" → "2026-06-21"
    // Uses reflection: ColumnDescriptor class and CsvConverter.convert(LinkedHashMap<ColumnDescriptor>)
    // are not yet implemented. Fails with ClassNotFoundException until production code is added.
    @Test
    void convertsDateTransformColumnUsingDeclaredPattern() throws Exception {
        Class<?> colDesc = Class.forName("com.finance.tools.csvconverter.ColumnDescriptor");
        Method verbatim = colDesc.getMethod("verbatim", String.class);
        Method dateTransform = colDesc.getMethod("dateTransform", String.class, String.class);

        LinkedHashMap<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("Bank",       verbatim.invoke(null, "bankName"));
        mapping.put("AcctNum",    verbatim.invoke(null, "accountNumber"));
        mapping.put("Type",       verbatim.invoke(null, "accountType"));
        mapping.put("Amount",     verbatim.invoke(null, "balance"));
        mapping.put("Curr",       verbatim.invoke(null, "currency"));
        mapping.put("as_of_date", dateTransform.invoke(null, "asOfDate", "dd-MMM-yy"));

        String input = "Bank,AcctNum,Type,Amount,Curr,as_of_date\nChase,000111222,CHECKING,1500.00,USD,21-Jun-26";

        Method convertMethod = CsvConverter.class.getMethod("convert", String.class, LinkedHashMap.class);
        String result = (String) convertMethod.invoke(converter, input, mapping);
        String[] lines = result.trim().split("\n");

        assertEquals(2, lines.length);
        assertEquals("bankName,accountNumber,accountType,balance,currency,asOfDate", lines[0]);
        assertEquals("Chase,000111222,CHECKING,1500.00,USD,2026-06-21", lines[1]);
    }

    // T-8 (new): unparseable date cell → CsvConversionException naming column, bad value, and row number
    // Uses reflection: same as T-7. Fails with ClassNotFoundException until production code is added.
    @Test
    void throwsCsvConversionExceptionForUnparseableDateCellWithColumnValueAndRowInMessage() throws Exception {
        Class<?> colDesc = Class.forName("com.finance.tools.csvconverter.ColumnDescriptor");
        Method verbatim = colDesc.getMethod("verbatim", String.class);
        Method dateTransform = colDesc.getMethod("dateTransform", String.class, String.class);

        LinkedHashMap<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("Bank",       verbatim.invoke(null, "bankName"));
        mapping.put("AcctNum",    verbatim.invoke(null, "accountNumber"));
        mapping.put("Type",       verbatim.invoke(null, "accountType"));
        mapping.put("Amount",     verbatim.invoke(null, "balance"));
        mapping.put("Curr",       verbatim.invoke(null, "currency"));
        mapping.put("as_of_date", dateTransform.invoke(null, "asOfDate", "dd-MMM-yy"));

        String input = "Bank,AcctNum,Type,Amount,Curr,as_of_date\nChase,000111222,CHECKING,1500.00,USD,not-a-date";

        Method convertMethod = CsvConverter.class.getMethod("convert", String.class, LinkedHashMap.class);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> convertMethod.invoke(converter, input, mapping));

        assertTrue(thrown.getCause() instanceof CsvConversionException,
                "Expected CsvConversionException but got: " + thrown.getCause());
        String message = thrown.getCause().getMessage();
        assertTrue(message.contains("as_of_date"), "Message must name input column");
        assertTrue(message.contains("not-a-date"), "Message must contain offending value");
        assertTrue(message.contains("1"), "Message must contain row number");
    }
}
