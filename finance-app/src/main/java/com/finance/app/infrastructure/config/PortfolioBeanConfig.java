package com.finance.app.infrastructure.config;

import com.finance.app.application.service.PortfolioApplicationService;
import com.finance.app.domain.port.in.FilterPortfolioUseCase;
import com.finance.app.domain.port.in.GetLatestPortfolioUseCase;
import com.finance.app.domain.port.in.ImportPortfolioUseCase;
import com.finance.app.domain.port.out.PortfolioFileParser;
import com.finance.app.domain.port.out.PortfolioRepository;
import com.finance.app.infrastructure.adapter.out.persistence.CsvFilePortfolioRepository;
import com.finance.app.infrastructure.adapter.out.persistence.OpenCsvPortfolioParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Clock;

@Configuration
public class PortfolioBeanConfig {

    @Bean
    public PortfolioFileParser portfolioFileParser(Clock clock) {
        return new OpenCsvPortfolioParser(clock);
    }

    @Bean
    public ImportPortfolioUseCase importPortfolioUseCase(PortfolioFileParser portfolioFileParser,
                                                          @Lazy PortfolioRepository portfolioRepository) {
        return new PortfolioApplicationService(portfolioFileParser, portfolioRepository);
    }

    @Bean
    public GetLatestPortfolioUseCase getLatestPortfolioUseCase(PortfolioFileParser portfolioFileParser,
                                                                @Lazy PortfolioRepository portfolioRepository) {
        return new PortfolioApplicationService(portfolioFileParser, portfolioRepository);
    }

    @Bean
    public FilterPortfolioUseCase filterPortfolioUseCase(PortfolioFileParser portfolioFileParser,
                                                          @Lazy PortfolioRepository portfolioRepository) {
        return new PortfolioApplicationService(portfolioFileParser, portfolioRepository);
    }

    @Bean
    public PortfolioRepository portfolioRepository(
            @Value("${finance.storage.portfolio-dir}") String dir) {
        return new CsvFilePortfolioRepository(dir);
    }
}
