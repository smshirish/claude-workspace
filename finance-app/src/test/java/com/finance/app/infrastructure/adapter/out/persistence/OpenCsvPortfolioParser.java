package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.model.PortfolioPosition;
import com.finance.app.domain.port.out.PortfolioFileParser;

import java.io.InputStream;
import java.util.List;

public class OpenCsvPortfolioParser implements PortfolioFileParser {

    @Override
    public List<PortfolioPosition> parse(InputStream inputStream, String fileName) {
        throw new UnsupportedOperationException("stub — not implemented yet");
    }
}
