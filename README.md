# COMP3290 Assessment 3

### Project Details:
* Group - 3
* David Newman - c3330268
* Caleb Garaty - c3430000

---

### Running the Compiler

Ensure you are in the correct working directory and compile the java files:

```
javac *.javac
```

Now, with a CD25 src file in the current directory, run the command:

```
java CD <file-with-ext>
```
The compiler should compile your source code into machine code.

---

### Semantic Analysis Features

We implemented all required semantic checks with our `SemanticAnalyzer.java` module.

### Code Generation Features

For code generation, the only feature we did *not* implement was the output format and `.mod` file. All code generation works as intended, but the output is the raw opcode literals rather than the bytecode. 

For example, this is a snippet of our code gen output:
```
LA1 0
LB 10
ARRAY
main:
ALLOC 2
rept_start_0:
LB 0
ST 2 16
LV2 1 0
LV2 2 16
INDEX
LV2 2 16
LB 1
ADD
ST ...
```
The way the opcodes are printed to the console is through the `Emitter.java` class. If we had more time, this could easily be extended to output to a `.mod` file and the opcode literals could be replaced with the actual opcodes from the `Opcode.java` enum.

Current implementation (`Emitter.emit()`):
```java
public void emit(String op, Object... args) {
        buf.append(op);
        for (Object a : args) buf.append(" ").append(a);
        buf.append("\n");
    }
```
Future change to allow proper opcode generation (`Emitter.emit`):
```java
public void emit(Opcode op, Object... args) {
        buf.append(op);
        for (Object a : args) buf.append(" ").append(a);
        buf.append("\n");
    }
```
To summarise, all code generation features work, apart from the final output in the `.mod` file.

---

### Supplied Source Files

There are 4 supplied source files that demonstrate different features of CD25:
* `program1.txt`
* `program2.txt`
* `program3.txt`
* `program4.txt`