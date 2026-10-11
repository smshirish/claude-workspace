package com.finance.tools.csvconverter;

import java.util.Map;

record AnonymisationResult(String csv, Map<String, String> anonymToReal) {}
