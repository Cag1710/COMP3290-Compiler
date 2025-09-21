import java.util.*;

final class ErrorReporter {
    private final OutputController out;
    private final List<CompilerError> errors = new ArrayList<>();

    ErrorReporter(OutputController out) {
        this.out = Objects.requireNonNull(out);
    }
    
    // whenever we detect a grammar violation e.g. expect() returned null
    void syntax(String message, Token at) {
        int line = (at != null ? at.line : -1); // pulls best available position from the token where the error was noticed, fallback -1
        int col = (at != null ? at.col : -1); // same thing with cols
        CompilerError e = new CompilerError("Syntax", message, line, col , at); // ties in with existing original lexer error reporter
        errors.add(e); // store error object in list to use later
        out.addError(e); // forward error to controller, shows up in lst file right away
    }

    // shows the list of all recorded errors
    List<CompilerError> all() {
        return errors;
    }

    // quick check if any errors were collected, so we can just check if we have errors before trying to print every error
    boolean hasErrors() {
        return !errors.isEmpty();
    }
}
