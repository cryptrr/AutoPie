import java.util.Map;
import java.nio.charset.StandardCharsets;
import com.autopi.autopieapp.data.services.ProcessManagerServiceKt;

/** Harmless host-side probes against compiled production Kotlin helpers. */
class SecurityProbe {
    static String bash(String script) throws Exception {
        Process p = new ProcessBuilder("/bin/bash", "--noprofile", "--norc", "-c", script)
            .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new AssertionError(output);
        return output;
    }
    public static void main(String[] args) throws Exception {
        String payload = "\"$(printf AUTOPIE_AUDIT_MARKER >&2)\"";
        String exports = ProcessManagerServiceKt.toShellExportCommands(Map.of("INPUT_TEXT", payload));
        String output = bash(exports + "\ntrue\n");
        if (!output.contains("AUTOPIE_AUDIT_MARKER")) throw new AssertionError("Injection not reproduced");
        System.out.println("CONFIRMED: quoted input executes command substitution during export");

        String ordinary = "$(printf SHOULD_NOT_EXECUTE >&2)";
        output = bash(ProcessManagerServiceKt.toShellExportCommands(Map.of("INPUT_TEXT", ordinary)) + "\ntrue\n");
        if (output.contains("SHOULD_NOT_EXECUTE")) throw new AssertionError("Unexpected control failure");
        System.out.println("CONTROL: unquoted input is safely escaped");

        output = bash("export API_TOKEN=AUTOPIE_FAKE_SECRET\n"
            + ProcessManagerServiceKt.commandScriptPreamble(false)
            + "printf '%s' \"$API_TOKEN\" >/dev/null\n");
        if (!output.contains("AUTOPIE_FAKE_SECRET")) throw new AssertionError("Trace leak not reproduced");
        System.out.println("CONFIRMED: production preamble leaks expanded secret into stderr");
    }
}
