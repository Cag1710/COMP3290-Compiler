import java.util.Map;
import java.util.List;

sealed interface Type {
    // records for each type e.g. a var declared as "real x;" will be given a "new Type.Real()"
    record Int() implements Type {}
    record Real() implements Type {}
    record Bool() implements Type {}
    record VoidT() implements Type {} // void with no return type
    // elem : int or real etc. size : size of the array
    record Array(Type elem, int size) implements Type {}
    // fields : each field name in a struct
    record Struct(Map<String, Type> fields) implements Type {}
    // unknown type
    record Error() implements Type {}

    // check whether a type is numeric
    static boolean isNumeric(Type t) {
        return t instanceof Int || t instanceof Real;
    }

    static boolean isInteger(Type t) {
        return t instanceof Int;
    }

    static boolean isReal(Type t) {
        return t instanceof Real;
    }
    
    // check whether a type is a boolean
    static boolean isBool(Type t) {
        return t instanceof Bool;
    }
}

// all possible symbols that can appear in the program
enum SymbolKind { VAR, CONST, PARAM, FUNC, TYPE, FIELD }

// all symbols must provide a name, a type, and a symbol kind
sealed interface Symbol permits VarSymbol, ParamSymbol, FuncSymbol, TypeSymbol, ConstSymbol {
    String name(); Type type(); SymbolKind kind();
}

record ConstSymbol (String name, Type type, Object value) implements Symbol {
    public SymbolKind kind() { return SymbolKind.CONST; }
}

record FuncSymbol (String name, Type returnType, List<Type> paramTypes) implements Symbol {
    public SymbolKind kind() { return SymbolKind.FUNC; }
    public Type type() { return returnType; }
}

record TypeSymbol (String name, Type type) implements Symbol {
    public SymbolKind kind() { return SymbolKind.TYPE; }
}

final class VarSymbol implements Symbol {
    private final String name;
    private final Type type;
    private int base = -1; // which memory area the variable lives in 0 -> constant area, 1 -> global, 2 -> current stack frame
    private int offset = 0; 
    private int sizeWords = 1; // how many words this symbol occupies

    public VarSymbol(String name, Type type) {
        this.name = name;
        this.type = type;
    }
    public String name() { return name; }
    public Type type() { return type; }
    public SymbolKind kind() { return SymbolKind.VAR; }

    // stores where it lives
    public void setAddr(int base, int offset) { this.base = base; this.offset = offset; }
    // which memory region the variable lives in
    public int base()   { return base; }
    // how far from base address
    public int offset() { return offset; }

    // how many words of memory this variable occupies
    public int sizeWords() { return sizeWords; }
    // assigns size, minimum is 1
    public void setSizeWords(int w) { this.sizeWords = Math.max(1, w); }
}

final class ParamSymbol implements Symbol {
    private final String name;
    private final Type type;
    private final boolean isConst;
    private int base = -1;
    private int offset = 0;
    private int sizeWords = 1;

    public ParamSymbol(String name, Type type, boolean isConst) {
        this.name = name;
        this.type = type;
        this.isConst = isConst;
    }
    public String name() { return name; }
    public Type type() { return type; }
    public SymbolKind kind() { return SymbolKind.PARAM; }
    public boolean isConst() { return isConst; }

    public void setAddr(int base, int offset) { this.base = base; this.offset = offset; }
    public int base()   { return base; }
    public int offset() { return offset; }

    public int sizeWords() { return sizeWords; }
    public void setSizeWords(int w) { this.sizeWords = Math.max(1, w); }
}
