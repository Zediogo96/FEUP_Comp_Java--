package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        addVisit("AccessMethod", this::dealWithAccessMethod);

        addVisit("BinaryOp", this::dealWithBinaryOperator);
        addVisit("RelationalOp", this::dealWithRelationalOperator);

        addVisit("UnaryOp", this::dealWithUnaryOperator);

        addVisit("IfElse", this::dealWithConditionalExpression);
        addVisit("While", this::dealWithConditionalExpression);

        addVisit("Assignment", this::dealWithAssignment);
        addVisit("ArrayAssignment", this::dealWithArrayAssignment);

        addVisit("Variable", this::dealWithVariable);
        addVisit("VarDeclaration", this::dealWithVarDeclaration);
        addVisit("Type", this::dealWithType);
        addVisit("NewObject", this::dealWithNewObject);
        addVisit("This", this::dealWithThis);

        addVisit("ArrayAccess", this::visitArrayAccess);
        addVisit("ArrayInit", this::dealWithArrayInit);

        addVisit("Integer", this::dealWithPrimitive);
        addVisit("Boolean", this::dealWithPrimitive);
        addVisit("Parenthesis", this::dealWithParenthesis);

        addVisit("Ret", this::dealWithReturn);

        setDefaultVisit(this::defaultVisit);
    }

    private Map.Entry<String, String> dealWithAccessMethod(JmmNode node, Boolean data) {

        JmmNode object = node.getChildren().get(0);
        JmmNode method = node.getChildren().get(1);

        Map.Entry<String, String> objectReturn = visit(object, true);
        Map.Entry<String, String> methodReturn = visit(method, true);

        List<String> parametersTypeNames = new ArrayList<>();

        if (node.getChildren().size() > 2) {
            for (JmmNode child : node.getChildren().subList(2, node.getChildren().size())) {
                Map.Entry<String, String> res = visit(child, true);
                parametersTypeNames.add(res.getKey().replace("(imported)", ""));
            }
        }

        if (methodReturn.getKey().equals("error")) {

            System.out.println(objectReturn.getKey());

            if (objectReturn.getKey().contains("imported") || (st.getSuper() != null && st.getImports().contains(st.getSuper()))) {
                return Map.entry("access", "null");
            } else if (st.getImports().contains(object.get("id"))) {
                return Map.entry("access", "null");
            } else if (objectReturn.getKey().equals(st.getClassName()) && (st.getMethod(method.get("id")) != null)) {

                List<Type> argumentsNames = st.getMethod(method.get("id")).getParameters().stream().map(Symbol::getType).toList();
                List<String> argumentsTypeNames = argumentsNames.stream().map(Type::getName).toList();

                if (argumentsTypeNames.equals(parametersTypeNames)) {
                    return Map.entry("access", "null");
                } else {
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Incorrect parameters in method call: " + method.get("id") + "() in class " + st.getClassName()));
                    return Map.entry("error", "null");
                }
            } else {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Method not found: " + method.get("id") + "() in class " + st.getClassName()));
                return Map.entry("error", "null");
            }

        }

        return Map.entry("null", "null");

    }

    private Map.Entry<String, String> dealWithThis(JmmNode node, Boolean data) {

        if (currentSCOPE.equals("MAIN")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Cannot use this in main method"));
            return Map.entry("error", "null");
        }

        if (node.getChildren().size() == 0) {
            return Map.entry(st.getClassName(), "true");
        } else if (node.getChildren().get(0).getKind().equals("MethodCall")) {
            JmmNode method = node.getChildren().get(0);
            Map.Entry<String, String> methodReturn = visit(method, true);

            String methodName = node.getChildren().get(0).get("method");

            if (methodReturn.getValue().equals("access") && st.getMethod(methodName) == null) {
                if (st.getSuper() != null && st.getImports().contains(st.getSuper())) {
                    return Map.entry("access", "null");
                } else if (st.getImports().contains(methodName)) {
                    return Map.entry("access", "null");
                } else {
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Method not found in class " + st.getClassName()));
                    return Map.entry("error", "null");
                }

            }
            return Map.entry(methodReturn.getValue(), "null");
        } else if (node.getChildren().get(0).getKind().equals("Variable")) {
            Map.Entry<Symbol, Boolean> variable = st.getField(node.getChildren().get(0).get("id"));
            if (variable == null) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable not found in class " + st.getClassName()));
                return Map.entry("error", "null");
            } else {
                Map.Entry<String, String> rightAssignment = visit(node.getChildren().get(1), true);
                if (!rightAssignment.getKey().equals(variable.getKey().getType().getName())) {
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable type mismatch"));
                    return Map.entry("error", "null");
                }

                st.initializeField(variable.getKey());
                return Map.entry(variable.getKey().getType().getName(), "true");
            }

        }
        return null;
    }

    private Map.Entry<String, String> dealWithUnaryOperator(JmmNode node, Boolean data) {

        JmmNode condition = node.getChildren().get(0);

        Map.Entry<String, String> conditionReturn = visit(condition, true);

        Map.Entry<String, String> dataReturn = Map.entry("boolean", "null");

        if (!conditionReturn.getKey().equals("boolean")) {
            dataReturn = Map.entry("error", "null");
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(condition.get("lineStart")), Integer.parseInt(condition.get("colStart")), "Unary expression not boolean"));
        }

        return dataReturn;
    }

    private Map.Entry<String, String> dealWithConditionalExpression(JmmNode node, Boolean data) {
        JmmNode condition = node.getChildren().get(0);

        Map.Entry<String, String> conditionReturn = visit(condition, true);

        Map.Entry<String, String> dataReturn = Map.entry("boolean", "null");

        if (!conditionReturn.getKey().equals("boolean")) {
            dataReturn = Map.entry("error", "null");
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(condition.get("lineStart")), Integer.parseInt(condition.get("colStart")), "Conditional expression not boolean"));
        }

        return dataReturn;
    }

    private Map.Entry<String, String> dealWithRelationalOperator(JmmNode node, Boolean data) {
        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        Map.Entry<String, String> leftReturn = visit(left, true);
        Map.Entry<String, String> rightReturn = visit(right, true);

        Map.Entry<String, String> dataReturn = Map.entry("boolean", "null");

        if (!leftReturn.getValue().equals("true") && left.getKind().equals("Variable")) {
            dataReturn = Map.entry("error", "null");
            if (data != null) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(left.get("lineStart")), Integer.parseInt(left.get("colStart")), "Left Member not initialized: " + left));
            }
        } else if (!rightReturn.getValue().equals("true") && right.getKind().equals("Variable")) {
            dataReturn = Map.entry("error", "null");
            if (data != null) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(right.get("lineStart")), Integer.parseInt(right.get("colStart")), "Right Member not initialized: " + right));
            }
        } else if (leftReturn.getKey() != null && rightReturn.getKey() != null) {
            if (leftReturn.getKey().equals("boolean") && rightReturn.getKey().equals("boolean") && (node.get("op").equals("&&") || node.get("op").equals("||"))) {
                dataReturn = Map.entry("boolean", "true");
            } else if (leftReturn.getKey().equals("int") && rightReturn.getKey().equals("int")) {
                dataReturn = Map.entry("boolean", "true");
            } else {
                dataReturn = Map.entry("error", "null");
                if (data != null) {
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types on Relational Operator: '" + leftReturn.getKey() + "' and '" + rightReturn.getKey() + "'"));
                }
            }
        }
        return dataReturn;
    }

    private Map.Entry<String, String> dealWithBinaryOperator(JmmNode node, Boolean data) {

        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        Map.Entry<String, String> leftReturn = visit(left, true);
        Map.Entry<String, String> rightReturn = visit(right, true);

        Map.Entry<String, String> dataReturn;

        if (!leftReturn.getValue().equals("true") && left.getKind().equals("Variable")) {
            dataReturn = Map.entry("error", "null");
            if (data != null) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(left.get("lineStart")), Integer.parseInt(left.get("colStart")), "Left Member not initialized: " + left));
                return dataReturn;
            }
        } else if (!rightReturn.getValue().equals("true") && right.getKind().equals("Variable")) {
            dataReturn = Map.entry("error", "null");
            if (data != null) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(right.get("lineStart")), Integer.parseInt(right.get("colStart")), "Right Member not initialized: " + right));
                return dataReturn;
            }
        } else if (leftReturn.getKey() != null && rightReturn.getKey() != null) {
            if (leftReturn.getKey().equals("int") && rightReturn.getKey().equals("int")) {
                return Map.entry("int", "true");
            } else if (leftReturn.getKey().equals("int[]") && rightReturn.getKey().equals("int[]")) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Array Variables cannot be used directly with an Binary Operator: '" + left.get("id") + "' " + node.get("op") + " '" + right.get("id") + "'"));
            } else {
                dataReturn = Map.entry("error", "null");
                if (data != null) {
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types on Binary Operator: '" + leftReturn.getKey() + " " + node.get("op") + " " + rightReturn.getKey() + "'"));
                    return dataReturn;
                }
            }
        }

        assert leftReturn.getKey() != null;
        return Map.entry(leftReturn.getKey(), "true");
    }

    private Map.Entry<String, String> dealWithNewObject(JmmNode node, Boolean space) {

        return Map.entry(node.get("id"), "object");
    }

    private Map.Entry<String, String> dealWithAssignment(JmmNode node, Boolean space) {

        /* List<JmmNode> children = node.getChildren(); */

        Map.Entry<String, String> assignment = visit(node.getChildren().get(0), true);
        Map.Entry<Symbol, Boolean> fieldToAssign;

        if (Objects.equals(currentSCOPE, "CLASS")) fieldToAssign = st.getField(node.get("id"));
        else fieldToAssign = currentMethod.getLocalVariable(node.get("id"));

        if (assignment.getKey().equals("error")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable for assignment not declared: " + node.get("id")));
            return Map.entry("error", "null");
        }

        Map.Entry<Symbol, Boolean> variable;
        if ((variable = currentMethod.getLocalVariable(node.get("id"))) == null) {
            variable = st.getField(node.get("id"));
        }

        String[] parts = assignment.getKey().split(" ");

        if (!(st.isPrimitiveType(parts[0]))) {

            boolean wasExtendedAsAssignment = st.getSuper().contains(fieldToAssign.getKey().getType().getName());
            boolean wasExtendedAsVariable = st.getSuper().contains(parts[0]);

            String fieldType = fieldToAssign.getKey().getType().getName();

            boolean wasImportedLeftPart = st.getImports().contains(fieldType) || fieldType.equals(st.getClassName());
            boolean wasImportedRightPart = st.getImports().contains(parts[0]) || parts[0].equals(st.getClassName());
            boolean isCurrentClassObject = fieldType.equals(st.getClassName()) && parts[0].equals(st.getClassName());

            if (!isCurrentClassObject) {
                if (fieldType.equals(st.getClassName())) {
                    if (!wasExtendedAsVariable) {
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Trying to assign the current Class Object: " + st.getClassName() + " but '" + parts[0].replace("(imported)", "") + "' was not extended'"));
                        return Map.entry("error", "null");
                    }
                } else if (parts[0].equals(st.getClassName())) {
                    if (!wasExtendedAsAssignment) {
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Trying to assign the current Class to an Object that was not extended"));
                        return Map.entry("error", "null");
                    } else return null;
                } else if (!wasImportedLeftPart && !wasImportedRightPart && !parts[0].equals("access")) {
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Class Not Imported!"));
                    return Map.entry("error", "null");
                }
            }
        }

        // IF assignment is related to access to an imported static method
        if (assignment.getKey().contains("imported") || assignment.getKey().contains("extended")) {
            variable.setValue(true);
            return null;
        }

        if (variable != null) {
            if (variable.getKey().getType().getName().equals(parts[0])) {
                if (!currentMethod.initializeField(variable.getKey())) st.initializeField(variable.getKey());
            } else if (!assignment.getKey().equals("access")) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types on Assignment: '" + variable.getKey().getType().getName() + "' and '" + assignment.getKey() + "'"));
                return null;
            }
        } else {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable for assignment not declared: " + node.get("id")));
            return Map.entry("error", "null");
        }

        return Map.entry("void", "null");
    }

    private Map.Entry<String, String> dealWithVarDeclaration(JmmNode node, Boolean space) {

        Map.Entry<String, String> type = visit(node.getChildren().get(0), true);
        Map.Entry<String, String> value = null;

        boolean inLineDeclaration = node.getChildren().size() > 1;

        if (inLineDeclaration) {
            value = visit(node.getChildren().get(1), true);
        }

        /* CHECK IF node.get("name") already exists in current method */
        if (currentMethod != null) {
            String varName = node.get("name");
            if (currentMethod.getParameters().stream().anyMatch(s -> s.getName().equals(varName))) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable '" + varName + "' already declared in current scope"));
                return Map.entry("error", "null");
            }
        }

        if (type.getKey().equals("error")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable type declared for variable '" + node.get("name") + "' not declared"));
            return Map.entry("error", "null");
        }

        if (inLineDeclaration) {
            if (!type.getKey().equals(value.getKey())) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types on Variable Declaration: '" + type.getKey() + "' and '" + value.getKey() + "'"));
                return Map.entry("error", "null");
            }
        }

        Map.Entry<Symbol, Boolean> variable;

        if (currentMethod != null && (currentSCOPE.equals("METHOD") || currentSCOPE.equals("MAIN"))) {
            variable = currentMethod.getLocalVariable(node.get("name"));
        } else {
            variable = st.getField(node.get("name"));
        }


        if (variable != null) {
            if (currentMethod != null && !currentMethod.initializeField(variable.getKey()))
                currentMethod.initializeField(variable.getKey());
            else st.initializeField(variable.getKey());
        }
        return null;
    }

    private Map.Entry<String, String> dealWithType(JmmNode node, Boolean space) {

        if (node.getKind().equals("ObjectType")) {

            for (String s : st.getImports()) {
                if (s.contains(node.get("type_"))) {
                    return Map.entry(s, "null");
                }
            }

            if (st.getImports().contains(node.get("type_"))) return Map.entry(node.get("type_"), "null");
            if (!st.getImports().contains(node.get("type_")) && !st.getSuper().contains(node.get("type_")) && !node.get("type_").equals(st.getClassName())) {
                return Map.entry("error", "null");
            }
        }
        return Map.entry(node.get("type_"), "null");
    }

    private Map.Entry<String, String> dealWithVariable(JmmNode node, Boolean data) {
        Map.Entry<Symbol, Boolean> field = null;

        if (currentSCOPE.equals("CLASS")) {
            field = st.getField(node.get("id"));
        } else if ((currentSCOPE.equals("METHOD") || currentSCOPE.equals("MAIN")) && currentMethod != null) {

            Map.Entry<Symbol, Boolean> tmp = currentMethod.getLocalVariable(node.get("id"));
            if (tmp != null) field = tmp;

            Map.Entry<Symbol, Boolean> tmp2 = st.getField(node.get("id"));
            if (tmp2 != null) field = tmp2;

            for (Symbol s : currentMethod.getParameters()) {
                if (s.getName().equals(node.get("id"))) {
                    field = Map.entry(new Symbol(s.getType(), s.getName()), true);
                }
            }

        }

        if (field != null && st.getImports().contains(field.getKey().getType().getName())) {
            return Map.entry(field.getKey().getType().getName() + "(imported)", "true");
        } else if (field != null && st.getSuper().contains(field.getKey().getType().getName())) {
            return Map.entry(field.getKey().getType().getName() + "(extended)", "true");
        } else if (node.get("id").equals("this")) {
            return Map.entry("method", "true");
        }

        if (field == null) {
            return Map.entry("error", "null");
        } else {
            return Map.entry(field.getKey().getType().getName(), field.getValue() ? "true" : "false");
        }
    }

    private Map.Entry<String, String> dealWithArrayAssignment(JmmNode node, Boolean space) {

        List<JmmNode> children = node.getChildren();

        JmmNode index = children.get(0);
        JmmNode value = children.get(1);

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
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types on Array Assignment: '" + variable.getKey().getType().getName() + "' and '" + valueReturn.getKey() + "'"));
            return null;
        }

        return Map.entry("void", "null");

    }

    private Map.Entry<String, String> dealWithMainDeclaration(JmmNode node, Boolean data) {
        currentSCOPE = "MAIN";

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

    private Map.Entry<String, String> visitArrayAccess(JmmNode node, Boolean data) {

        JmmNode array = node.getChildren().get(0);
        JmmNode index = node.getChildren().get(1);

        Map.Entry<String, String> indexReturn = visit(index, true);

        Map.Entry<Symbol, Boolean> temp1 = st.getField(array.get("id"));
        Map.Entry<Symbol, Boolean> temp2 = currentMethod.getLocalVariable(array.get("id"));

        Symbol arraySymbol = null;

        if (temp1 != null) arraySymbol = temp1.getKey();
        else if (temp2 != null) arraySymbol = temp2.getKey();

        if (arraySymbol != null && !arraySymbol.getType().getName().equals("int[]")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(array.get("lineStart")), Integer.parseInt(array.get("colStart")), "Variable is not an array: " + array.get("id")));
            return Map.entry("error", "null");
        } else if (!indexReturn.getKey().equals("int")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(index.get("lineStart")), Integer.parseInt(index.get("colStart")), "Array index is not an Integer: " + index.get("id")));
            return Map.entry("error", "null");
        } else if (arraySymbol == null) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(array.get("lineStart")), Integer.parseInt(array.get("colStart")), "Variable not declared: " + array.get("id")));
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

        return Map.entry("int[]", "null");
    }

    private Map.Entry<String, String> dealWithReturn(JmmNode node, Boolean space) {
        JmmNode child = node.getChildren().get(0);
        String returnType = visit(child, true).getKey();

        if (returnType.contains("access")) {
            return Map.entry("access", "null");
        }

        if (returnType.equals("error")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Return statement is not valid"));
            return Map.entry("error", "null");
        }

        returnType = returnType.replace("(imported)", "");

        if (!returnType.equals(currentMethod.getReturnType().getName().replace("(imported)", ""))) {

            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Mismatched types on return statement: '" + currentMethod.getReturnType().getName() + "' and '" + returnType + "'"));
            return Map.entry("error", "null");
        }

        return Map.entry(returnType, "true");
    }

    private Map.Entry<String, String> dealWithMethodCall(JmmNode node, Boolean space) {

        if (node.getKind().equals("Length")) {
            return Map.entry("length", "null");
        }

        String methodName = node.get("method");

        if (st.getMethod(methodName) != null) {


            List<Type> argumentsNames = st.getMethod(methodName).getParameters().stream().map(Symbol::getType).toList();
            List<String> argumentsTypeNames = argumentsNames.stream().map(Type::getName).toList();

            List<String> parametersNames = new ArrayList<>();

            for (JmmNode child : node.getChildren()) {
                String parameterName = visit(child, true).getKey();
                parametersNames.add(parameterName);
            }


            /* GET RETURN TYPE FOR METHOD */
            String returnType = st.getMethod(methodName).getReturnType().getName();
            boolean isArray = returnType.contains("[]");


            if (parametersNames.equals(argumentsTypeNames))
                return Map.entry("method", returnType + (isArray ? "[]" : ""));
            else {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Method found but arguments are incorrect: " + methodName + "\n\t\t" + "- Parameters required: " + parametersNames + "\n\t\t" + "- Arguments used: " + argumentsNames));
                return Map.entry("error", "noSuchMethod");
            }

        } else {
            if (st.getSuper() == null || st.getSuper().equals("Object")) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Method not found: " + methodName));
                return Map.entry("error", "noSuchMethod");
            } else {
                return Map.entry("method", "access");
            }
        }
    }

    private Map.Entry<String, String> dealWithParenthesis(JmmNode node, Boolean data) {
        return visit(node.getChildren().get(0), data);
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

    @Override
    protected void buildVisitor() {

    }
}

