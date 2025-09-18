import java.util.HashMap;

// symbol table to store identifiers and their associated types (idk if its better to have the full Token or just TokenType)
public class SymbolTable {
    private HashMap<String, Token> table;

    public SymbolTable() {
        table = new HashMap<>();
    }

    public void add(String iden, Token token) {
        table.put(iden, token);
    }

    // if we need we can also add funcs to getType directly...
    public Token get(String iden) {
        return table.get(iden);
    }
}