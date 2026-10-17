package com.finance.app.domain.port.out;

import com.finance.app.domain.model.PortfolioPosition;

import java.io.InputStream;
import java.util.List;

public interface PortfolioFileParser {

    List<PortfolioPosition> parse(InputStream inputStream, String fileName);
}
