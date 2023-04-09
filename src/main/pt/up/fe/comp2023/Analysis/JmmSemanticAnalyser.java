package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JmmSemanticAnalyser extends PreorderJmmVisitor<Boolean, Map.Entry<String, String>> {

    private final MySymbolTable st;
    private final List<Report> reports;
    private String currentSCOPE;
    private Method currentMethod;

    public JmmSemanticAnalyser(MySymbolTable st, List<Report> reports) {

        this.st = st;
        this.reports = reports;

        addVisit("ClassDeclaration", this::dealWithClassDeclaration);
        addVisit("MainMethodDeclaration", this::dealWithMainDeclaration);
        addVisit("MethodDeclaration", this::dealWithMethodDeclaration);
        addVisit("MethodCall", this::dealWithMethodCall);

        addVisit("BinaryOp", this::dealWithBinaryOperator);
        addVisit("Assignment", this::dealWithAssignment);
        addVisit("ArrayAssignment", this::dealWithArrayAssignment);

        addVisit("Variable", this::dealWithVariable);

        addVisit("ArrayAccess", this::visitArrayAccess);
        addVisit("ArrayInit", this::dealWithArrayInit);


        addVisit("Integer", this::dealWithPrimitive);
        addVisit("Boolean", this::dealWithPrimitive);

        addVisit("Ret", this::dealWithReturn);

        setDefaultVisit(this::defaultVisit);
    }

    private Map.Entry<String, String> dealWithBinaryOperator(JmmNode node, Boolean data) {

        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        Map.Entry<String, String> leftReturn = visit(left, true);
        Map.Entry<String, String> rightReturn = visit(right, true);

        System.out.println("Left: " + leftReturn.getKey() + " " + leftReturn.getValue());
        System.out.println("Right: " + rightReturn.getKey() + " " + rightReturn.getValue());

        Map.Entry<String, String> dataReturn = Map.entry("int", "null");

        if (!leftReturn.getValue().equals("true") && left.getKind().equals("Variable")) {
            dataReturn = Map.entry("error", "null");
            if (data != null) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(left.get("lineStart")), Integer.parseInt(left.get("colStart")), "Left Member not initialized: " + left));
                return dataReturn;
            }
        }
        else if (!rightReturn.getValue().equals("true") && right.getKind().equals("Variable")) {
            dataReturn = Map.entry("error", "null");
            if (data != null) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(right.get("lineStart")), Integer.parseInt(right.get("colStart")), "Right Member not initialized: " + right));
                return dataReturn;
            }
        }
        else if (leftReturn.getKey() != null && rightReturn.getKey() != null) {
            if (leftReturn.getKey().equals("int") && rightReturn.getKey().equals("int")) {
                dataReturn = Map.entry("int", "true");
            } else {
                dataReturn = Map.entry("error", "null");
                if (data != null) {
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types on Binary Operator: '" + leftReturn.getKey() + " " + node.get("op") + " " + rightReturn.getKey() + "'"));
                    return dataReturn;
                }
            }
        }

        return Map.entry(leftReturn.getKey(), "true");
    }

    private Map.Entry<String, String> dealWithAssignment(JmmNode node, Boolean space) {

        List<JmmNode> children = node.getChildren();

        if (children.size() == 1) {
            Map.Entry<String, String> assignment = visit(node.getChildren().get(0), true);

            if (assignment.getKey().equals("error")) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable for assignment not declared: " + node.get("id")));
                return null;
            }

            Map.Entry<Symbol, Boolean> variable;
            if ((variable = currentMethod.getLocalVariable(node.get("id"))) == null) {
                variable = st.getField(node.get("id"));
            }

            // IF assignment is related to access to an imported static method
            if (assignment.getKey().equals("access")) {
                variable.setValue(true);
                return null;
            }

            String[] parts = assignment.getKey().split(" ");

            // Matching Types
            if (variable.getKey().getType().getName().equals(parts[0])) {
                if (!currentMethod.initializeField(variable.getKey())) st.initializeField(variable.getKey());
            } else {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types: '" + variable.getKey().getType().getName() + "' and '" + assignment.getKey() + "'"));
                return null;
            }
        }

        return Map.entry("void", "null");
    }

    private Map.Entry<String, String> dealWithArrayAssignment(JmmNode node, Boolean space) {

        List<JmmNode> children = node.getChildren();

        JmmNode index = children.get(0);
        JmmNode value = children.get(1);

        System.out.println("Value: " + value.getKind());

        Map.Entry<String, String> indexReturn = visit(index, true);

        if (!indexReturn.getKey().equals("int")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Array index must be of type int"));
            return Map.entry("error", "null");
        }

        Map.Entry<String, String> valueReturn = visit(value, true);

        if (valueReturn.getKey().equals("error")) {
            return Map.entry("error", "null");
        }

        Map.Entry<Symbol, Boolean> variable;

        if ((variable = currentMethod.getLocalVariable(node.get("id"))) == null) {
            variable = st.getField(node.get("id"));
        }

        if (variable == null) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable not declared: " + node.get("id")));
            return Map.entry("error", "null");
        }

        if (variable.getKey().getType().getName().equals("int[]") && valueReturn.getKey().equals("int")) {
            st.initializeField(variable.getKey());
        } else {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types: '" + variable.getKey().getType().getName() + "' and '" + valueReturn.getKey() + "'"));
            return null;
        }

        return Map.entry("void", "null");

    }

    private Map.Entry<String, String> dealWithMainDeclaration(JmmNode node, Boolean data) {
        currentSCOPE = "METHOD";

        try {
            currentMethod = st.getMethod("main");
        } catch (Exception e) {
            currentMethod = null;
            e.printStackTrace();
        }

        return null;
    }

    private Map.Entry<String, String> dealWithClassDeclaration(JmmNode node, Boolean data) {
        currentSCOPE = "CLASS";
        return Map.entry("class", "true");
    }

    private Map.Entry<String, String> dealWithMethodDeclaration(JmmNode node, Boolean data) {
        currentSCOPE = "METHOD";

        try {
            currentMethod = st.getMethod(node.get("name"));
        } catch (Exception e) {
            currentMethod = null;
            e.printStackTrace();
        }

        return null;
    }

    private Map.Entry<String, String> dealWithPrimitive(JmmNode node, Boolean data) {
        String return_type = switch (node.getKind()) {
            case "Integer" -> "int";
            case "Boolean" -> "boolean";
            default -> "error";
        };

        return Map.entry(return_type, "true");
    }

    private Map.Entry<String, String> dealWithVariable(JmmNode node, Boolean data) {
        Map.Entry<Symbol, Boolean> field = null;

        if (currentSCOPE.equals("CLASS")) {
            field = st.getField(node.get("id"));
        } else if (currentSCOPE.equals("METHOD") && currentMethod != null) {

            Map.Entry<Symbol, Boolean> tmp = currentMethod.getLocalVariable(node.get("id"));
            if (tmp != null) field = tmp;

            Map.Entry<Symbol, Boolean> tmp2 = st.getField(node.get("id"));
            if (tmp2 != null) field = tmp2;
        }

        if (field == null && st.getImports().contains(node.get("id"))) {
            return Map.entry("access", "true");
        } else if (field == null && node.get("id").equals("this")) {
            return Map.entry("method", "true");
        }

        if (field == null) {
            return Map.entry("error", "null");
        }
        else {
            return Map.entry(field.getKey().getType().getName(), field.getValue() ? "true" : "false");
        }
    }

    private Map.Entry<String, String> visitArrayAccess(JmmNode node, Boolean data) {

        Map.Entry<String, String> dataReturn = Map.entry("int", "null");

        JmmNode array = node.getChildren().get(0);
        JmmNode index = node.getChildren().get(1);

        Map.Entry<String, String> indexReturn = visit(index, true);

        Map.Entry<Symbol, Boolean> temp1 = st.getField(array.get("id"));
        Map.Entry<Symbol, Boolean> temp2 = currentMethod.getLocalVariable(array.get("id"));

        Symbol arraySymbol = null;

        if (temp1 != null) arraySymbol = temp1.getKey();
        else if (temp2 != null) arraySymbol = temp2.getKey();

        if (!arraySymbol.getType().getName().equals("int[]")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(array.get("lineStart")), Integer.parseInt(array.get("colStart")), "Variable is not an array: " + array.get("id")));
            return Map.entry("error", "null");
        } else if (!indexReturn.getKey().equals("int")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(index.get("lineStart")), Integer.parseInt(index.get("colStart")), "Array index is not an Integer: " + index.get("id")));
            return Map.entry("error", "null");
        }

        return Map.entry("int", "true");
    }

    private Map.Entry<String, String> dealWithArrayInit(JmmNode node, Boolean data) {
        JmmNode size = node.getChildren().get(0);
        Map.Entry<String, String> sizeReturn = visit(size, true);

        if (!sizeReturn.getKey().equals("int")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(size.get("lineStart")), Integer.parseInt(size.get("colStart")), "Array init size is not an Integer: " + size));
            return Map.entry("error", "null");
        }

        return Map.entry("int []", "null");
    }
    private Map.Entry<String, String> dealWithReturn(JmmNode node, Boolean space) {
        JmmNode child = node.getChildren().get(0);
        String returnType = visit(child, true).getKey();

        if (returnType.equals("error")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Return statement is not valid"));
            return Map.entry("error", "null");
        }
        if (!returnType.equals(currentMethod.getReturnType().getName())) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types: '" + currentMethod.getReturnType().getName() + "' and '" + returnType + "'"));
            return Map.entry("error", "null");
        }

        return Map.entry(returnType, "true");
    }

    private Map.Entry<String, String> defaultVisit(JmmNode node, Boolean data) {
        Map.Entry<String, String> dataReturn = Map.entry("int", "null");

        for (JmmNode child : node.getChildren()) {
            Map.Entry<String, String> childReturn = visit(child, data);
            if (childReturn.getKey().equals("error")) {
                dataReturn = childReturn;
            }
        }

        return dataReturn;
    }

    private Map.Entry<String, String> dealWithMethodCall(JmmNode node, Boolean space) {
        if (node.getKind().equals("Length")) {
            return Map.entry("length", "null");
        }

        String methodName = node.get("method");

        if(st.getMethod(methodName) != null) {
            return Map.entry("method", "true");
        } else {
            if (st.getSuper() != null) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Method not found: " + methodName));
                return Map.entry("error", "noSuchMethod");
            } else {
                return Map.entry("method", "access");
            }
        }
    }

    @Override
    protected void buildVisitor() {

    }
}

