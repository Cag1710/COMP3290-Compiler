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

record VarSymbol (String name, Type type) implements Symbol { 
    public SymbolKind kind() { return SymbolKind.VAR; }
}

record ParamSymbol (String name, Type type, Boolean isConst) implements Symbol {
    public SymbolKind kind() { return SymbolKind.PARAM; }
}

record FuncSymbol (String name, Type returnType, List<Type> paramTypes) implements Symbol {
    public SymbolKind kind() { return SymbolKind.FUNC; }
    public Type type() { return returnType; }
}

record TypeSymbol (String name, Type type) implements Symbol {
    public SymbolKind kind() { return SymbolKind.TYPE; }
}
