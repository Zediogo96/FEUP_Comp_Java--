package pt.up.fe.comp2023.ollir.optimizations;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.Map;

public class ConstPropVisitor extends AJmmVisitor<ConstPropParameters, Boolean> {

    public ConstPropVisitor() {
        setDefaultVisit(this::defaultVisit);
        addVisit("program", this::defaultVisit);
        addVisit("classDeclaration", this::defaultVisit);
        addVisit("methodDeclaration", this::defaultVisit);
        addVisit("mainMethodDeclaration", this::defaultVisit);
        addVisit("statement", this::defaultVisit);
        addVisit("expression", this::defaultVisit);

        addVisit("Variable", this::dealWithVariable);
        addVisit("Assignment", this::dealWithAssignment);
    }

    public Boolean defaultVisit(JmmNode node, ConstPropParameters constPropPar) {
        boolean changes = false;

        for (JmmNode child : node.getChildren()) {
            changes = visit(child, constPropPar) || changes;
        }

        return changes;
    }

    private boolean dealWithVariable(JmmNode node, ConstPropParameters constPropagationParam) {

        if (constPropagationParam.toRemoveAssigned()) return false;

        System.out.println("NODE: " + node);

        String name = node.get("id");
        Map<String, String> constants = constPropagationParam.getConstants();

        if (constants.containsKey(name)) {
            JmmNode newNode;

            switch (constants.get(name)) {
                case "true", "false" -> newNode = new JmmNodeImpl("Boolean");
                default -> newNode = new JmmNodeImpl("Integer");
            }

            newNode.put("value", constants.get(name));
            node.replace(newNode);
            return true;
        }
        return false;
    }

    public boolean dealWithAssignment(JmmNode node, ConstPropParameters constPropPar) {
        boolean changes = false;

        boolean assignmentIsArrayVariable = node.getChildren().get(0).getKind().equals("ArrayAccess");

        if (constPropPar.toRemoveAssigned()) {
            changes = visit(node.getChildren().get(1), constPropPar) || changes;
        }

        if (assignmentIsArrayVariable) return false;

        String assigneeName = node.get("id");

        constPropPar.getConstants().remove(assigneeName);

        if (!constPropPar.toRemoveAssigned()) {
            JmmNode newNode = node.getChildren().get(0);
            switch (newNode.getKind()) {
                case "Integer", "Boolean" -> {
                    constPropPar.getConstants().put(assigneeName, newNode.get("value"));
                }
            }
        }

        return changes;
    }


    @Override
    protected void buildVisitor() {

    }
}
