---
Architecture Overview

The app is a Spring Boot web application for personal finance management. It follows Hexagonal Architecture (Ports & Adapters), also known as Clean Architecture.

Layers

┌──────────────────────────────────────────────────────────┐
│  Infrastructure (Adapters)                               │
│  ┌──────────────────┐   ┌─────────────────────────────┐  │
│  │  Inbound          │   │  Outbound                   │  │
│  │  - Web (Thymeleaf)│   │  - CsvFileUserRepository    │  │
│  │  - Security       │   │  - BcryptPasswordHasher     │  │
│  └────────┬─────────┘   └──────────────┬──────────────┘  │
│           │                            │                  │
│  ┌────────▼────────────────────────────▼──────────────┐  │
│  │  Application Layer                                  │  │
│  │  - AuthenticationApplicationService                 │  │
│  └────────────────────────┬───────────────────────────┘  │
│                           │                              │
│  ┌────────────────────────▼───────────────────────────┐  │
│  │  Domain (Core)                                      │  │
│  │  Models: User, UserId, Username, HashedPassword,    │  │
│  │          Role                                       │  │
│  │  Ports In:  AuthenticateUserUseCase                 │  │
│  │  Ports Out: UserRepository, PasswordHasher          │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘

Key Design Decisions

┌────────────────┬────────────────────────────────────────────────┐
│    Concern     │                     Choice                     │
├────────────────┼────────────────────────────────────────────────┤
│ Architecture   │ Hexagonal (Ports & Adapters)                   │
├────────────────┼────────────────────────────────────────────────┤
│ Web framework  │ Spring MVC + Thymeleaf (server-side rendering) │
├────────────────┼────────────────────────────────────────────────┤
│ Authentication │ Spring Security with form login + BCrypt       │
├────────────────┼────────────────────────────────────────────────┤
│ Persistence    │ Flat CSV file at ~/.finance-app/data/users.csv │
├────────────────┼────────────────────────────────────────────────┤
│ Authorization  │ Single role: OWNER                             │
└────────────────┴────────────────────────────────────────────────┘

Flow: Login Request

1. Browser POSTs credentials → SecurityConfig intercepts via Spring Security's form login
2. FinanceUserDetailsService (inbound security adapter) loads the user via UserRepository
3. Spring Security's DaoAuthenticationProvider verifies the BCrypt password
4. On success, redirects to /dashboard; on failure, back to /login?error

Notable Characteristics

- No database — persistence is a CSV file, making the app zero-infrastructure for local single-user use
- Domain is framework-free — the domain package has no Spring annotations; all wiring is in infrastructure.config
- Only one user role (OWNER), suggesting this is a single-owner personal finance tool
- AuthenticationApplicationService exists as a domain-level use case but Spring Security's own auth pipeline (DaoAuthenticationProvider) is what actually drives login — the use case isn't directly wired into the security filter chain
- Tests cover domain models, the application service, the CSV repository, and the login flow end-to-end