import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free integration tests. Run: javac HLInt.java HLIntTest.java && java HLIntTest */
public class HLIntTest {
    static int passed;
    static final Path ROOT = Path.of("test-output");
    static void check(String name, String source, int expectedCode, String expectedOutput) throws Exception {
        Path directory = ROOT.resolve(name);
        Files.createDirectories(directory);
        Path file = directory.resolve("input.HL");
        Files.writeString(file, source);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out, originalErr = System.err;
        int code;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            code = HLInt.run(file, directory);
        } finally { System.setOut(originalOut); System.setErr(originalErr); }
        String actual = stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (code != expectedCode || !actual.equals(expectedOutput))
            throw new AssertionError(name + ": code=" + code + ", output=" + actual + ", diagnostic=" + stderr);
        if (!Files.readString(directory.resolve("NOSPACES.TXT")).equals(source.replace(" ", "")))
            throw new AssertionError(name + ": incorrect NOSPACES.TXT");
        if (!Files.exists(directory.resolve("RES_SYM.TXT"))) throw new AssertionError(name + ": missing token file");
        if (expectedCode == 1 && !stderr.toString(StandardCharsets.UTF_8).contains("Line "))
            throw new AssertionError(name + ": missing location");
        passed++;
    }
    static void good(String name, String source, String output) throws Exception {
        check(name, source, 0, "NO ERROR(S) FOUND\n" + output);
    }
    static void bad(String name, String source) throws Exception { check(name, source, 1, "ERROR\n"); }
    public static void main(String[] args) throws Exception {
        good("prog1", Files.readString(Path.of("PROG1.HL")), "5\n");
        good("prog2", Files.readString(Path.of("PROG2.HL")), "4.25\n");
        good("prog3", Files.readString(Path.of("PROG3.HL")), "3\n");
        good("arithmetic", "x:integer;x=9+9-3;output<<x;output<<0.1+0.2;output<<4+2.56;", "15\n0.30\n6.56\n");
        good("conditions", "if(2>1)output<<1;if(1>2)output<<0;if(1<2)output<<2;if(2<1)output<<0;if(2==2)output<<3;if(2==1)output<<0;if(2!=1)output<<4;if(2!=2)output<<0;", "1\n2\n3\n4\n");
        good("case", "X:INTEGER; x:=3; If(X<5) Output<<x;", "3\n");
        good("strings", "output<<\"hello world\";output<<\u201chello again\u201d;output<<\u201dhello\u201d;", "hello world\nhello again\nhello\n");
        good("negative", "x:integer;x:=-3;output<<x;output<<(3+2)-1;", "-3\n4\n");
        good("nested", "if(1<2)if(2>1)output<<\"yes\";if(2<1)output<<missing;", "yes\n");
        good("promotion", "d:double;d:=3;output<<d;", "3.00\n");
        bad("semicolon", "x:integer;x:=5\noutput<<x;");
        bad("false_branch_syntax", "if(1>2)output<<;");
        bad("undeclared", "x:=3;");
        bad("uninitialized", "x:integer;output<<x;");
        bad("duplicate", "x:integer;X:double;");
        bad("fractional", "x:integer;x:=1.25;");
        bad("precision", "output<<1.234;");
        bad("multidigit", "output<<12;");
        bad("operator", "output<<2*3;");
        bad("comparison", "if(1>=2)output<<1;");
        bad("string", "output<<\"hello;");
        bad("type", "x:float;");
        bad("reserved_name", "if:integer;");
        bad("missing_body", "if(1<2)");
        bad("no_partial_output", "output<<\"hidden\";x:=2;");
        good("tokens", "x: integer; output<<\"if + integer\";", "if + integer\n");
        String symbols = Files.readString(ROOT.resolve("tokens/RES_SYM.TXT")).replace("\r\n", "\n");
        if (!symbols.equals(":\ninteger\n;\noutput\n<<\n;\n")) throw new AssertionError("Incorrect reserved words/symbols: " + symbols);
        System.out.println("PASS: " + passed + " integration cases and exact token-file verification.");
    }
}
