package pt.up.fe.comp2023.ollir;


import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.List;
import java.util.stream.Collectors;

public class OllirUtils {

    public static String getOllirType(Type jmmType) {
        //System.out.println(jmmType.getName());
        switch (jmmType.getName()) {
            case "void" -> {
                return ".V";
            }
            case "boolean" -> {
                return ".bool";
            }
            case "int[]" -> {
                if (jmmType.isArray())
                    return ".array.i32";
                else
                    return " ";
            }
            case "int" -> {
                if (jmmType.isArray()) {
                    return ".array.i32";
                } else {
                    return ".i32";
                }
            }
            case "String" -> {
                if(jmmType.isArray()) {
                    return ".array.String";
                } else {
                    return ".String";
                }
            }
            default -> {
                return "." + jmmType.getName();
            }
        }
    }

    public static String getCode(Symbol symbol) {
        return symbol.getName() + getOllirType(symbol.getType());
    }

    public static String getOperator(String op) {
        return switch(op) {
            case "+" -> "+.i32";
            case "-" -> "-.i32";
            case "*" -> "*.i32";
            case "/" -> "/.i32";
            case "&&" -> "&&.bool";
            case "||" -> "||.bool";
            case "<" -> "<.bool";
            case "<=" -> "<=.bool";
            case ">" -> ">.bool";
            case ">=" -> ">=.bool";
            default -> throw new IllegalArgumentException("Unexpected value: " + op);
        };
    }

    public static String getOperatorType(String op) {
        return switch(op) {
            case "+" -> ".i32";
            case "-" -> ".i32";
            case "*" -> ".i32";
            case "/" -> ".i32";
            case "&&" -> ".bool";
            case "||" -> ".bool";
            case "<" -> ".bool";
            case "<=" -> ".bool";
            case ">" -> ".bool";
            case ">=" -> ".bool";
            default -> throw new IllegalArgumentException("Unexpected value: " + op);
        };
    }

    public static String getReturnOperandType(String op) {
        return switch(op) {
            case "+" -> ".i32";
            case "-" -> ".i32";
            case "*" -> ".i32";
            case "/" -> ".i32";
            case "&&" -> ".bool";
            case "||" -> ".bool";
            case "<" -> ".bool";
            case "<=" -> ".bool";
            case ">" -> ".bool";
            case ">=" -> ".bool";
            default -> throw new IllegalArgumentException("Unexpected value: " + op);
        };
    }


    public static  String getInvokeType(String invokee, SymbolTable st) {
        if (invokee.equals("this")) {
            return "invokevirtual";
        }

        List<String> imports = st.getImports();

        //conver the list of lists (imports) to a list of strings

        List<String> importNames = imports.stream()
                .map(str -> str.replaceAll("\\[|\\]", ""))
                .toList();

        //System.out.println("IMPORTS" + importNames);

        for (String _import : importNames) {
            String[] tokens = _import.split("\\.");
            if (tokens[tokens.length - 1].equals(invokee)) {
                return "invokestatic";
            }
        }

        return "invokevirtual";
    }

    public static String getDefaultVarDecl(Type jmmType) {
        //System.out.println(jmmType.getName());
        switch (jmmType.getName()) {
            case "int" -> {
                return " :=.i32 0.i32";
            }
            case "boolean" -> {
                return " :=.bool 1.bool";
            }

            default -> {
                return "." + jmmType.getName();
            }
        }
    }
}
