package com.finance.tools.csvconverter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java -jar csv-converter.jar <input.csv> <mapping.json> <output.csv>");
            System.exit(1);
        }

        String inputFile  = args[0];
        String mappingFile = args[1];
        String outputFile = args[2];

        // Read mapping — LinkedHashMap preserves key insertion order for column ordering
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> columnMapping = mapper.readValue(
                new File(mappingFile),
                new TypeReference<LinkedHashMap<String, String>>() {});

        String inputCsv  = Files.readString(Path.of(inputFile));
        String outputCsv = new CsvConverter().convert(inputCsv, columnMapping);

        Files.writeString(Path.of(outputFile), outputCsv);
        System.out.println("Written: " + outputFile);
    }
}
