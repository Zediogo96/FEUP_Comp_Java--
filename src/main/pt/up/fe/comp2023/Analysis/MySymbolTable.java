package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MySymbolTable implements SymbolTable {

    private final List<String> imports = new ArrayList<>();
    private String className, superClassName;
    private final Map<Symbol, Boolean> fields = new HashMap<>();
    private final List<Method> methods = new ArrayList<>();
    private Method currentMethod;

    public static Type getType(JmmNode node, String attribute) {
        Type type;
        String temp = node.get(attribute);

        type = switch (temp) {
            case "int" -> new Type("int", false);
            case "int[]" -> new Type("int[]", true);
            default -> new Type(temp, false);
        };

        return type;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setSuperClassName(String superClassName) {
        this.superClassName = superClassName;
    }

    public void addImport(String importName) {
        imports.add(importName);
    }

    public void addField(Symbol field, boolean isStatic) {
        fields.put(field, isStatic);
    }

    public boolean fieldExists(String name) {
        for (Symbol field : fields.keySet()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

//    get field
    public Symbol getField(String name) {
        for (Symbol field : fields.keySet()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    public boolean initializeField(Symbol field) {
        if (fields.containsKey(field)) {
            fields.put(field, true);
            return true;
        }
        return false;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return superClassName;
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public List<Symbol> getFields() {
        return new ArrayList<>(fields.keySet());
    }

    @Override
    public List<String> getMethods() {
        return null;
    }

    @Override
    public Type getReturnType(String s) {
        return null;
    }

    @Override
    public List<Symbol> getParameters(String methodName) {
        for (Method m : this.methods) {
            if (m.getName().equals(methodName)) {
                return m.getParameters();
            }
        }
        return null;
    }


    @Override
    public List<Symbol> getLocalVariables(String s) {
        return null;
    }
}
