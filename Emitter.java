public final class Emitter {
    private final StringBuilder buf = new StringBuilder(); // everytime emit is called we append to this, building up the assembly text
    private int labelCount = 0; // keep track of how many labels we have made

    // instruction writer, e.g. emit("LOAD", "R1", "R2", "R3") or emit("LOAD", 2, 8)
    public void emit(String op, Object... args) {
        buf.append(op);
        for (Object a : args) buf.append(" ").append(a);
        buf.append("\n");
    } // buf after emit("LOAD", 2, 8) looks like LOAD 2 8

    // makes a unique label each time its called, newLabel("loop") creates loop_0
    public String newLabel(String prefix) {
        return prefix + "_" + (labelCount++);
    }

    // add a label to the code, this is so we can use it for jumping to it
    // loop_0:
    // ADD 2, 8 or whatever
    // BR loop_0 <- jumps back to loop_0, essentially this method adds a marker to the assembly
    public void label(String name) {
        buf.append(name).append(":\n");
    }

    // returns the code
    public String toString() {
        return buf.toString();
    }
}
