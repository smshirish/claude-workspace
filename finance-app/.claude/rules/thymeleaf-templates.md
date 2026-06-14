# applies to: src/main/resources/templates/**
All Thymeleaf templates must follow these conventions:

- Every interactive element needs data-testid="[feature]-[element]"  e.g. data-testid="order-submit-button", data-testid="user-email-input"
- Dynamic rows: th:attr="data-testid='order-row-' + ${order.id}"
- Conditional blocks: data-testid on outermost rendered element
- Never use th:field-generated name attributes as Playwright selectors