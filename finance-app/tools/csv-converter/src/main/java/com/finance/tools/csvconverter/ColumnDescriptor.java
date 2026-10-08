package com.finance.tools.csvconverter;

record ColumnDescriptor(String outputColumn, String dateFromPattern) {
    public static ColumnDescriptor verbatim(String outputColumn) {
        return new ColumnDescriptor(outputColumn, null);
    }
    public static ColumnDescriptor dateTransform(String outputColumn, String fromPattern) {
        return new ColumnDescriptor(outputColumn, fromPattern);
    }
    boolean isDateTransform() { return dateFromPattern != null; }
}
