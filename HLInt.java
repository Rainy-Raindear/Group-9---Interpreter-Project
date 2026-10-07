import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/** A lexer, recursive-descent parser, and interpreter for the HL language. */
public class HLInt {
    enum Kind { NAME, NUMBER, STRING, SYMBOL, EOF }
    record Token(Kind kind, String text, int line, int column) {}
    static final List<String> WORDS = List.of("integer", "double", "output", "if");
    static final List<String> SYMBOLS = List.of(":=", "<<", "==", "!=", ":", ";", "=", "+", "-", "(", ")", ">", "<");

    static class HLError extends RuntimeException {
        HLError(Token token, String message) {
            super("Line " + token.line() + ", column " + token.column() + ": " + message);
        }
    }

    static class Lexer {
        final String source;
        final List<Token> tokens = new ArrayList<>();
        final StringBuilder reserved = new StringBuilder();
        int offset, line = 1, column = 1;
        Lexer(String source) { this.source = source; }
        char current() { return source.charAt(offset); }
        void advance() {
            if (current() == '\n') { line++; column = 1; } else { column++; }
            offset++;
        }
        void add(Kind kind, String value, int row, int col) {
            tokens.add(new Token(kind, value, row, col));
            if (kind == Kind.SYMBOL || (kind == Kind.NAME && WORDS.contains(value.toLowerCase(Locale.ROOT)))) {
                reserved.append(value).append(System.lineSeparator());
            }
        }
        List<Token> scan() {
            while (offset < source.length()) {
                char c = current();
                if (Character.isWhitespace(c) || c == '\uFEFF') { advance(); continue; }
                int start = offset, row = line, col = column;
                if (Character.isLetter(c) || c == '_') {
                    while (offset < source.length() && (Character.isLetterOrDigit(current()) || current() == '_')) advance();
                    add(Kind.NAME, source.substring(start, offset), row, col);
                } else if (Character.isDigit(c)) {
                    while (offset < source.length() && Character.isDigit(current())) advance();
                    if (offset < source.length() && current() == '.') {
                        advance();
                        while (offset < source.length() && Character.isDigit(current())) advance();
                    }
                    add(Kind.NUMBER, source.substring(start, offset), row, col);
                } else if (c == '"' || c == '\u201c' || c == '\u201d') {
                    advance();
                    int contentStart = offset;
                    while (offset < source.length() && current() != '"' && current() != '\u201c' && current() != '\u201d' && current() != '\n' && current() != '\r') advance();
                    if (offset >= source.length() || current() == '\n' || current() == '\r')
                        throw new HLError(new Token(Kind.STRING, "", row, col), "Unterminated string.");
                    String value = source.substring(contentStart, offset);
                    advance();
                    add(Kind.STRING, value, row, col);
                } else {
                    String symbol = offset + 1 < source.length() ? source.substring(offset, offset + 2) : "";
                    if (!SYMBOLS.contains(symbol)) symbol = String.valueOf(c);
                    if (!SYMBOLS.contains(symbol)) throw new HLError(new Token(Kind.SYMBOL, symbol, row, col), "Unexpected character '" + c + "'.");
                    for (int i = 0; i < symbol.length(); i++) advance();
                    add(Kind.SYMBOL, symbol, row, col);
                }
            }
            tokens.add(new Token(Kind.EOF, "<end of file>", line, column));
            return tokens;
        }
    }

    record Value(BigDecimal number, boolean decimal) {
        String display() { return decimal ? number.setScale(2, RoundingMode.UNNECESSARY).toPlainString() : number.toPlainString(); }
    }
    static class Variable {
        final boolean decimal;
        Value value;
        Variable(boolean decimal) { this.decimal = decimal; }
    }
    static class Context {
        final Map<String, Variable> variables = new LinkedHashMap<>();
        final StringBuilder output = new StringBuilder();
    }
    interface Expression { Value evaluate(Context context); }
    interface Statement { void execute(Context context); }
    static String key(Token token) { return token.text().toLowerCase(Locale.ROOT); }
    static Variable variable(Context context, Token token) {
        Variable variable = context.variables.get(key(token));
        if (variable == null) throw new HLError(token, "Undeclared variable '" + token.text() + "'.");
        return variable;
    }

    static class Parser {
        final List<Token> tokens;
        int index;
        Parser(List<Token> tokens) { this.tokens = tokens; }
        Token peek() { return tokens.get(index); }
        Token take() { return tokens.get(index++); }
        boolean at(String text) { return peek().text().equalsIgnoreCase(text); }
        boolean match(String text) { if (!at(text)) return false; take(); return true; }
        void expect(String text) {
            if (!match(text)) throw new HLError(peek(), "Expected '" + text + "', found '" + peek().text() + "'.");
        }
        Token identifier() {
            Token token = peek();
            if (token.kind() != Kind.NAME || WORDS.contains(key(token))) throw new HLError(token, "Expected a variable name.");
            return take();
        }
        List<Statement> parse() {
            List<Statement> statements = new ArrayList<>();
            while (peek().kind() != Kind.EOF) statements.add(statement());
            return statements;
        }
        Statement statement() {
            if (match("output")) {
                expect("<<");
                if (peek().kind() == Kind.STRING) {
                    String value = take().text();
                    expect(";");
                    return context -> context.output.append(value).append(System.lineSeparator());
                }
                Expression value = expression();
                expect(";");
                return context -> context.output.append(value.evaluate(context).display()).append(System.lineSeparator());
            }
            if (match("if")) {
                expect("(");
                Expression left = expression();
                Token comparison = take();
                if (!List.of(">", "<", "==", "!=").contains(comparison.text())) throw new HLError(comparison, "Expected >, <, ==, or !=.");
                Expression right = expression();
                expect(")");
                Statement body = statement();
                return context -> {
                    int result = left.evaluate(context).number().compareTo(right.evaluate(context).number());
                    boolean condition = switch (comparison.text()) {
                        case ">" -> result > 0;
                        case "<" -> result < 0;
                        case "==" -> result == 0;
                        default -> result != 0;
                    };
                    if (condition) body.execute(context);
                };
            }
            Token name = identifier();
            if (match(":")) {
                Token type = take();
                if (!type.text().equalsIgnoreCase("integer") && !type.text().equalsIgnoreCase("double")) throw new HLError(type, "Expected integer or double.");
                boolean decimal = type.text().equalsIgnoreCase("double");
                expect(";");
                return context -> {
                    if (context.variables.containsKey(key(name))) throw new HLError(name, "Variable already declared: " + name.text());
                    context.variables.put(key(name), new Variable(decimal));
                };
            }
            if (!match(":=") && !match("=")) throw new HLError(peek(), "Expected ':', ':=', or '=' after variable name.");
            Expression expression = expression();
            expect(";");
            return context -> {
                Variable target = variable(context, name);
                Value value = expression.evaluate(context);
                if (!target.decimal && value.number().stripTrailingZeros().scale() > 0) throw new HLError(name, "Cannot assign a fractional value to an integer.");
                target.value = new Value(value.number().setScale(target.decimal ? 2 : 0, RoundingMode.UNNECESSARY), target.decimal);
            };
        }
        Expression expression() {
            Expression result = atom();
            while (at("+") || at("-")) {
                String operator = take().text();
                Expression left = result, right = atom();
                result = context -> {
                    Value a = left.evaluate(context), b = right.evaluate(context);
                    return new Value(operator.equals("+") ? a.number().add(b.number()) : a.number().subtract(b.number()), a.decimal() || b.decimal());
                };
            }
            return result;
        }
        Expression atom() {
            if (match("-")) {
                Expression value = atom();
                return context -> { Value v = value.evaluate(context); return new Value(v.number().negate(), v.decimal()); };
            }
            if (match("(")) { Expression value = expression(); expect(")"); return value; }
            if (peek().kind() == Kind.NUMBER) {
                Token number = take();
                if (!number.text().matches("[0-9](\\.[0-9]{1,2})?")) throw new HLError(number, "Numeric literals need one integer digit and at most two decimal places.");
                Value value = new Value(new BigDecimal(number.text()), number.text().contains("."));
                return context -> value;
            }
            Token name = identifier();
            return context -> {
                Variable target = variable(context, name);
                if (target.value == null) throw new HLError(name, "Variable has no assigned value: " + name.text());
                return target.value;
            };
        }
    }

    /** Returns 0 on success, 1 on a source error, and 2 on an I/O error. */
    static int run(Path sourcePath, Path outputDirectory) {
        try {
            String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
            Files.createDirectories(outputDirectory);
            // Literal space removal is a required artifact; lex the original to retain strings and locations.
            Files.writeString(outputDirectory.resolve("NOSPACES.TXT"), source.replace(" ", ""), StandardCharsets.UTF_8);
            Lexer lexer = new Lexer(source);
            List<Token> tokens;
            try { tokens = lexer.scan(); }
            finally { Files.writeString(outputDirectory.resolve("RES_SYM.TXT"), lexer.reserved, StandardCharsets.UTF_8); }
            List<Statement> program = new Parser(tokens).parse();
            Context context = new Context();
            for (Statement statement : program) statement.execute(context);
            System.out.println("NO ERROR(S) FOUND");
            System.out.print(context.output);
            return 0;
        } catch (HLError error) {
            System.out.println("ERROR");
            System.err.println(error.getMessage());
            return 1;
        } catch (IOException error) {
            System.out.println("ERROR");
            System.err.println("File error: " + error.getMessage());
            return 2;
        }
    }
    public static void main(String[] args) {
        if (args.length > 2) {
            System.err.println("Usage: java HLInt.java [source.HL] [output-directory]");
            System.exit(2);
        }
        String filename;
        if (args.length == 0) {
            System.out.print("HL source file [PROG1.HL]: ");
            Scanner input = new Scanner(System.in);
            filename = input.hasNextLine() ? input.nextLine().trim() : "";
            if (filename.isEmpty()) filename = "PROG1.HL";
        } else filename = args[0];
        System.exit(run(Path.of(filename), args.length == 2 ? Path.of(args[1]) : Path.of(".")));
    }
}
