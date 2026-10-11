package com.finance.app.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.app.domain.port.out.AccountIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class JsonFileAccountIdResolver implements AccountIdResolver {

    private static final Logger log = LoggerFactory.getLogger(JsonFileAccountIdResolver.class);

    private final Map<String, String> anonToReal;

    public JsonFileAccountIdResolver(String mappingFilePath) {
        this.anonToReal = loadMapping(mappingFilePath);
    }

    @Override
    public String resolve(String id) {
        return anonToReal.getOrDefault(id, id);
    }

    private static Map<String, String> loadMapping(String path) {
        File file = new File(path);
        if (!file.exists()) {
            log.info("Anonymisation mapping file not found at {} — account numbers displayed as-is", path);
            return Collections.emptyMap();
        }
        try {
            Map<String, String> mapping = new ObjectMapper().readValue(
                    file, new TypeReference<Map<String, String>>() {});
            log.info("Loaded anonymisation mapping: {} entries from {}", mapping.size(), path);
            return Collections.unmodifiableMap(mapping);
        } catch (Exception e) {
            log.warn("Failed to load anonymisation mapping from {} — account numbers displayed as-is: {}",
                    path, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
