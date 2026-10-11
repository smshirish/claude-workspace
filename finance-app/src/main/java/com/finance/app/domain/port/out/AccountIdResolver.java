package com.finance.app.domain.port.out;

public interface AccountIdResolver {
    /**
     * Returns the real account number for the given ID.
     * If no mapping exists (ID is not anonymous), returns the input unchanged.
     */
    String resolve(String id);
}
