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
java -jar target/csv-converter-1.0-SNAPSHOT-jar-with-dependencies.jar <input.csv> <mapping.json> <output-dir>
```

| Argument | Description |
|---|---|
| `input.csv` | Source CSV with any column names |
| `mapping.json` | Maps input column names → output column names |
| `output-dir` | Output directory (created if missing). Receives `account_anony_<asOfDate>.csv` (asOfDate as `YYYY-MM-DD`; the file must contain exactly one) and `anonym_to_real_id.json`, which is merged across runs: known accounts keep their ANON id, new accounts get the next free id |

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

All six output columns required by the Finance App must be present (`accountType` is defaulted if missing, see below):

| Output column | Constraints |
|---|---|
| `bankName` | Non-blank string |
| `accountNumber` | Non-blank string |
| `accountType` | One of: `SAVINGS`, `CHECKING`, `CREDIT`, `INVESTMENT`. Blank values, or a missing column, default to `SAVINGS` |
| `balance` | Decimal number (negative allowed, e.g. `-350.00`) |
| `currency` | Non-blank string |
| `asOfDate` | Date; must be a single distinct value across the file (used in the output filename) |

Extra columns in the input that are not listed in `mapping.json` are ignored.

### Date columns

A mapping value may be an object to reformat a date to ISO `yyyy-MM-dd`:

```json
"as_of_date": { "to": "asOfDate", "type": "date", "from": "dd-MMM-yy" }
```

### accountType defaulting

If a row's `accountType` is blank, or the column is absent (not in `mapping.json`, or its source column is not in the input), the value `SAVINGS` is used. Each defaulted row is logged to stderr, by data row number only (no account numbers):

```
[WARN] data row 2: accountType blank, defaulted to SAVINGS
[WARN] data row 1: accountType column missing, defaulted to SAVINGS
```

Values that are present but not a known type are kept as is.

---

## Example

**input.csv**
```
Bank,AcctNum,Type,Amount,Curr,as_of_date,Notes
Chase,000111222,CHECKING,1500.00,USD,21-Jun-26,primary
Ally,333444555,,800.00,USD,21-Jun-26,
Citi,666777888,CREDIT,-350.00,USD,21-Jun-26,
```

**mapping.json**
```json
{
  "Bank":       "bankName",
  "AcctNum":    "accountNumber",
  "Type":       "accountType",
  "Amount":     "balance",
  "Curr":       "currency",
  "as_of_date": { "to": "asOfDate", "type": "date", "from": "dd-MMM-yy" }
}
```

**Command**
```bash
java -jar target/csv-converter-1.0-SNAPSHOT-jar-with-dependencies.jar input.csv mapping.json out/
```

**out/account_anony_2026-06-21.csv (output)**
```
bankName,accountNumber,accountType,balance,currency,asOfDate
Chase,ANON-0001,CHECKING,1500.00,USD,2026-06-21
Ally,ANON-0002,SAVINGS,800.00,USD,2026-06-21
Citi,ANON-0003,CREDIT,-350.00,USD,2026-06-21
```

(`Ally`'s blank type was defaulted to `SAVINGS` and logged.)

**out/anonym_to_real_id.json** — merged on every run: an account already listed keeps its ID, a new account gets the next free ID, and existing entries are never removed. It holds real account numbers, so keep it private (gitignored).

Upload `account_anony_<asOfDate>.csv` via the Finance App **Accounts → Import** page.

---

## Error handling

| Error | Cause |
|---|---|
| `Mapped input column not found in CSV header: 'X'` | A key in `mapping.json` does not match any column in the input CSV header |
| `Input CSV is empty` | The input file has no content at all |
| `Multiple asOfDate values found: [...]` | The file contains more than one distinct as-of date |
| `No asOfDate values found` / `no 'asOfDate' column` | The converted CSV has no usable `asOfDate` |
| `Existing mapping file is unreadable` | `anonym_to_real_id.json` in the output dir is corrupt; it is left untouched |
| `Invalid anonymisation ID in existing mapping` | An existing key in `anonym_to_real_id.json` is not of the form `ANON-NNNN` |

---

## Tests

```bash
mvn test                                 # unit tests
test/smoke_account_conversion.bash       # end-to-end check of account_conversion.bash (needs the built jar)
```
