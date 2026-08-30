# CSV Converter

Converts an account CSV with arbitrary column names into the format expected by the Finance App upload endpoint.

---

## Prerequisites

- Java 21
- Maven 3.x

---

## Build

```bash
cd finance-app/tools/csv-converter
mvn package -q
```

Produces `target/csv-converter-1.0-SNAPSHOT-jar-with-dependencies.jar`.

---

## Usage

```bash
java -jar target/csv-converter-1.0-SNAPSHOT-jar-with-dependencies.jar <input.csv> <mapping.json> <output.csv>
```

| Argument | Description |
|---|---|
| `input.csv` | Source CSV with any column names |
| `mapping.json` | Maps input column names → output column names |
| `output.csv` | Destination file in Finance App format |

---

## mapping.json

Keys are input column names; values are the required output column names. Column order in the output follows the order of keys in the file.

```json
{
  "Bank":          "bankName",
  "AcctNum":       "accountNumber",
  "Type":          "accountType",
  "Amount":        "balance",
  "Curr":          "currency"
}
```

All five output columns required by the Finance App must be present:

| Output column | Constraints |
|---|---|
| `bankName` | Non-blank string |
| `accountNumber` | Non-blank string |
| `accountType` | One of: `SAVINGS`, `CHECKING`, `CREDIT`, `INVESTMENT` |
| `balance` | Decimal number (negative allowed, e.g. `-350.00`) |
| `currency` | Non-blank string |

Extra columns in the input that are not listed in `mapping.json` are ignored.

---

## Example

**input.csv**
```
Bank,AcctNum,Type,Amount,Curr,Notes
Chase,000111222,CHECKING,1500.00,USD,primary
Ally,333444555,SAVINGS,800.00,USD,
Citi,666777888,CREDIT,-350.00,USD,
```

**mapping.json**
```json
{
  "Bank":    "bankName",
  "AcctNum": "accountNumber",
  "Type":    "accountType",
  "Amount":  "balance",
  "Curr":    "currency"
}
```

**Command**
```bash
java -jar target/csv-converter-1.0-SNAPSHOT-jar-with-dependencies.jar input.csv mapping.json accounts.csv
```

**accounts.csv (output)**
```
bankName,accountNumber,accountType,balance,currency
Chase,000111222,CHECKING,1500.00,USD
Ally,333444555,SAVINGS,800.00,USD
Citi,666777888,CREDIT,-350.00,USD
```

Upload `accounts.csv` via the Finance App **Accounts → Import** page.

---

## Error handling

| Error | Cause |
|---|---|
| `Mapped input column not found in CSV header: 'X'` | A key in `mapping.json` does not match any column in the input CSV header |
| `Input CSV is empty` | The input file has no content at all |
