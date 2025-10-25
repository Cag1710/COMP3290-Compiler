public class CodeGenerator {
    private final Emitter em;
    private final SymbolTable table;

    public CodeGenerator(SymbolTable table, Emitter em) {
        this.table = table;
        this.em = em;
    }

    public void generate(StNode root) {
        visitProgram(root);
    }

    private void visitProgram(StNode root) {
        
    }
}
