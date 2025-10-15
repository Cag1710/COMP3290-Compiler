import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// barebones symbol table to store identifiers and their associated types
final class SymbolTable {

    // a stack of lexical scopes, push/pop as we enter/exit functions and blocks
    private final Deque<Scope> stack = new ArrayDeque<>();

    public SymbolTable () {
        stack.push(new Scope(null));
    }

    // push a new scope
    public void enter () {
        stack.push(new Scope(stack.peek()));
    }

    // pop the current scope
    public void exit () {
        stack.pop();
    }
    
    // add a symbol to current scope
    public boolean define (Symbol s) {
        return stack.peek().define(s);
    }

    // look up a name starting in the current scope and walk outwards
    public Symbol resolve (String n) {
        return stack.peek().resolve(n);
    }
}

final class Scope {
    final Scope parent; // link to outer scope
    final Map<String, Symbol> entries = new HashMap<>(); // for the entries in this level

    Scope(Scope parent) {
        this.parent = parent;
    }

    // insert if absent
    boolean define (Symbol s) {
        return entries.putIfAbsent(s.name(), s) == null; 
    }
    // search this scope, and then search parents
    Symbol resolve (String n) {
        for (Scope s = this; s != null; s = s.parent) {
            Symbol x = s.entries.get(n);
            if (x != null) {
                return x;
            }        
        }
        return null;
    }
}