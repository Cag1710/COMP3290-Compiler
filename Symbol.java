public class Symbol {
    private String iden;
    private String type;    // int, real, func, arr, etc.
    // scope to be added in semantic analysis

    public Symbol(String iden, String type) {
        this.iden = iden;
        this.type = type;
    }

    public String getIden() { return iden; }
    public String getType() { return type; }

    @Override
    public String toString() {
        return type + " " + iden;
    }
}