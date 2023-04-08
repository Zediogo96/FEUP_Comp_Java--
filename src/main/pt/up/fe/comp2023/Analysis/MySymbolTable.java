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

    public static Type getTypeFromAttr(JmmNode node, String attribute) {
        Type type;
        String temp = node.get(attribute);

        type = switch (temp) {
            case "int" -> new Type("int", false);
            case "int[]" -> new Type("int[]", true);
            case "boolean" -> new Type("boolean", false);
            case "String" -> new Type("String", false);
            default -> new Type(temp, false);
        };

        return type;
    }

    public static Type getTypeFromNode(JmmNode node) {
        Type type;
        String temp = node.getKind();

        type = switch (temp) {
            case "Int" -> new Type("int", false);
            case "Array" -> new Type("int", true);
            case "Boolean" -> new Type("boolean", false);
            case "Class" -> new Type(node.get("name"), false);
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

    public Method getMethod(String methodName, List<Type> parameters, Type returnType) {
        for (Method m : methods) {
            if (m.getName().equals(methodName) && m.getParameters().size() == parameters.size() && m.getReturnType().equals(returnType)) {
                boolean equal = true;
                for (int i = 0; i < parameters.size(); i++) {
                    if (!m.getParameters().get(i).getType().equals(parameters.get(i))) {
                        equal = false;
                        break;
                    }
                }
                if (equal) {
                    return m;
                }
            }
        }
        return null;
    }

    public void addMethod(String methodName, Type returnType, List<Symbol> parameters) {
        currentMethod = new Method(methodName, returnType, parameters);
        methods.add(currentMethod);
    }

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

    public Method getCurrentMethod() {
        return currentMethod;
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
        List<String> methodNames = new ArrayList<>();
        for (Method m : methods) {
            methodNames.add(m.getName());
        }
        return methodNames;
    }

    @Override
    public Type getReturnType(String s) {

        for (Method m : methods) {
            if (m.getName().equals(s)) {
                return m.getReturnType();
            }
        }
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
        for (Method m : methods) {
            if (m.getName().equals(s)) {
                return m.getLocalVariables();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("---------------- Symbol Table ----------------\n");
        sb.append("1) Imports: \n");
        for (String imp : imports) {
            sb.append("\t-").append(imp).append("\n");
        }
        sb.append("2) Class: ").append(className).append("\n");
        sb.append("3) Super: ").append(superClassName).append("\n");
        sb.append("4) Fields: (").append("Size: ").append(fields.size()).append(")\n");
        for (Symbol field : fields.keySet()) {
            sb.append("\t-").append(field.getName()).append(" : ").append(field.getType()).append(" (").append(fields.get(field) ? "static" : "non-static").append(")\n");
        }
        sb.append("5) Methods: (").append("Size: ").append(methods.size()).append(")\n");
        for (Method m : methods) {
            sb.append("\t-").append(m.getName()).append(" : ").append(m.getReturnType()).append(" (").append(m.getParameters().size()).append(" parameters)\n");
        }
        sb.append("------------------------------------------------\n");

        return sb.toString();
    }
}
