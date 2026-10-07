# HL simple interpreter

This project implements the supplied CSS125P specification in Java. `HLInt.java` is the interpreter (the specification's `HLInt.XXX`), with no external libraries. It reads an HL file, writes the two required text files, checks syntax, and executes the program.

## Run on Windows

Requires JDK 17 or newer. From this project directory:

```powershell
java HLInt.java PROG1.HL
java HLInt.java PROG2.HL
java HLInt.java PROG3.HL
java HLInt.java DEMO.HL
java HLInt.java ERROR_DEMO.HL
```

Double-click `run.bat` for a filename prompt. Press Enter to run `PROG1.HL`. You can also run `run.bat PROG2.HL`. Paths with spaces must be quoted. To choose a separate output folder, use `java HLInt.java PROG1.HL results`.

The launcher checks `JAVA_HOME` and installed JDKs under `Program Files\Java` before falling back to PATH. On this computer, the PATH Java shortcut stalled during verification; the JDK at `C:\Program Files\Java\jdk-21.0.11\bin` compiled and ran the project successfully. For direct commands here, replace `java` with `& 'C:\Program Files\Java\jdk-21.0.11\bin\java.exe'` and `javac` with the corresponding `javac.exe` path in PowerShell, or use the launcher.

Successful runs print `NO ERROR(S) FOUND`, followed by program output. The required samples print `5`, `4.25`, and `3`, respectively. The error demonstration prints `ERROR` and a diagnostic for its missing semicolon. Exit codes are 0 for success, 1 for a program error, and 2 for a file/usage error.

## Required output files

- `NOSPACES.TXT`: source with every literal space removed, including spaces inside strings; line breaks and tabs remain. This follows the document's wording “removes all spaces.”
- `RES_SYM.TXT`: reserved words and symbols, one occurrence per line in source order. Duplicates are retained; names, numbers, and string contents are excluded. Multi-character symbols such as `:=` and `<<` stay together. Original capitalization is retained.

Each run replaces these files in the output directory (the current directory by default). A lexical error leaves tokens collected before the error in `RES_SYM.TXT`. An unreadable input file does not regenerate artifacts. The interpreter parses the original source so removing spaces does not damage strings or join separate names.

## Supported language

```text
x:integer;
y:double;
x:=3;
y:=1.25;
x=3+2;
output<<"hello world";
output<<x+y;
if(x<5)
    output<<x;
```

Declarations use `integer` or `double`. Assignment accepts `:=` and `=` because both occur in the specification. Addition and subtraction evaluate left to right. Integer literals have one digit; decimal literals have one digit before the point and one or two after it. Computed results may exceed one digit. Decimals print with two decimal places and use `BigDecimal` for exact addition and subtraction.

Conditions support `>`, `<`, `==`, and `!=`, followed by exactly one statement. Nesting is allowed. There are no braces, `else`, loops, multiplication, or division. Parenthesized arithmetic and unary minus are supported conveniences. Keywords and variable names are case-insensitive to accommodate `If`, `Output`, and `X` in the supplied examples. Identifiers begin with a letter or underscore and continue with letters, digits, or underscores. Reserved words cannot be variable names.

Straight and typographic double quotation marks delimit strings, including the repeated closing quotes used in the document. Strings must stay on one line; escape sequences and comments are not part of this language. Each output statement prints a new line.

Variables must be declared before execution reaches their use and assigned before reading. Redeclaration and assigning fractional results to an integer are errors. Integer values can be assigned to doubles. A conditional declaration exists only when its branch executes; execution errors in skipped branches are not evaluated, but their syntax is always checked.

## Implementation walkthrough

1. **File processing** reads UTF-8 input and writes `NOSPACES.TXT`.
2. **Lexer** scans names, numbers, strings, and symbols while tracking line/column locations; it also collects `RES_SYM.TXT`.
3. **Recursive-descent parser** converts tokens into executable statement and expression objects. It validates the entire program, including skipped conditional bodies.
4. **Execution context** stores typed variables in a map and evaluates statements in order. Output is buffered so an invalid program does not print partial results.
5. **Error handling** prints the exact status requested, with an additional diagnostic on standard error.

## Verification

```powershell
javac HLInt.java HLIntTest.java
java HLIntTest
```

The test suite checks the three supplied programs, decimal accuracy, all four comparisons in both directions, capitalization, strings, generated artifacts, and invalid syntax/type/variable cases. Test artifacts are stored in `test-output/`.

## Presentation sequence

1. Run `PROG1.HL` and open `NOSPACES.TXT` and `RES_SYM.TXT` to explain preprocessing and token recognition.
2. Run `PROG2.HL` to demonstrate typed variables and mixed arithmetic (`4.25`).
3. Run `PROG3.HL` to show a true condition (`3`).
4. Run `DEMO.HL` to show strings, both assignment operators, arithmetic, and a false branch that produces no output.
5. Run `ERROR_DEMO.HL`, explain the diagnostic, and add the missing semicolon during your demonstration.
6. Explain the lexer, parser, variable map, and execution flow in `HLInt.java`.

The original specification remains unchanged. Its stated presentation deadline is October 7, 2026, at class time. Review your class's implementation requirements and be ready to explain the design choices above.
