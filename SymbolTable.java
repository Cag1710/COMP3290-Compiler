import java.util.ArrayList;
import java.util.HashMap;

// barebones symbol table to store identifiers and their associated types
public class SymbolTable {
    private HashMap<String, ArrayList<Symbol>> table;

    public SymbolTable() {
        table = new HashMap<>();
    }

    public void add(String iden, Symbol symbol) {
        ArrayList<Symbol> list = table.get(iden);
        if (list == null) {
            list = new ArrayList<>();
            table.put(iden, list);
        }
        list.add(symbol);
    }
}