package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Method {

    private final String name;
    private final Type returnType;

    private final List<Map.Entry<Symbol, String>> parameters = new ArrayList<>();
    private final Map<Symbol, Boolean> localVariables = new HashMap<>();

    public Method(String name, Type returnType, List<Symbol> parameters) {
        this.name = name;
        this.returnType = returnType;
        for (Symbol parameter : parameters) {
            this.parameters.add(Map.entry(parameter, parameter.getName()));
        }
    }

    public String getName() {
        return name;
    }

    public Type getReturnType() {
        return returnType;
    }

    public List<Symbol> getParameters() {
        List<Symbol> parameters = new ArrayList<>();

        for (Map.Entry<Symbol, String> parameter : this.parameters) {
            parameters.add(parameter.getKey());
        }
        return parameters;
    }

    public List<Symbol> getLocalVariables() {
        return new ArrayList<>(this.localVariables.keySet());
    }

    public Map.Entry<Symbol, Boolean> getLocalVariable(String name) {
        for (Map.Entry<Symbol, Boolean> localVariable : localVariables.entrySet()) {
            if (localVariable.getKey().getName().equals(name)) {
                return localVariable;
            }
        }
        return null;
    }

    public boolean localVariableExists(String name) {
        for (Symbol localVariable : localVariables.keySet()) {
            if (localVariable.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean initializeField(Symbol field) {
        if (localVariables.containsKey(field)) {
            localVariables.put(field, true);
            return true;
        }
        return false;
    }

    public void addLocalVariable(Symbol localVariable, boolean isStatic) {
        localVariables.put(localVariable, isStatic);
    }

    @Override
    public String toString() {
        StringBuilder returnString = new StringBuilder("Method {\n" +
                "\tname = '" + name + "'\n," +
                "\treturnType=" + returnType +
                ", \n parameters = ");

        for (Map.Entry<Symbol, String> parameter : parameters) {
            returnString.append("\t\t").append(parameter.getKey().getType()).append(" ").append(parameter.getValue()).append("\n");
        }

        for (Symbol localVariable : localVariables.keySet()) {
            returnString.append("\t\t").append(localVariable.getType()).append(" ").append(localVariable.getName()).append("\n");
        }

        returnString.append("}");

        return returnString.toString();
    }
}
