package pt.up.fe.comp2023.ollir.optimizations;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

import java.util.Map;

public class ConstPropVisitor extends PreorderJmmVisitor<ConstPropParameters, Boolean> {

    public ConstPropVisitor() {
        setDefaultVisit(this::defaultVisit);
        addVisit("program", this::defaultVisit);
        addVisit("classDeclaration", this::defaultVisit);
        addVisit("methodDeclaration", this::defaultVisit);
        addVisit("mainMethodDeclaration", this::defaultVisit);
        addVisit("statement", this::defaultVisit);
        addVisit("expression", this::defaultVisit);

        addVisit("While", this::dealWithWhile);
        addVisit("Variable", this::dealWithVariable);
        addVisit("Assignment", this::dealWithAssignment);
    }

    public Boolean defaultVisit(JmmNode node, ConstPropParameters constPropPar) {
        boolean changes = false;

        for (JmmNode child : node.getChildren()) changes = visit(child, constPropPar) || changes;

        return changes;
    }

    private boolean dealWithVariable(JmmNode node, ConstPropParameters constPropPar) {

        if (constPropPar.toRemoveAssigned()) return false;

        String name = node.get("id");
        Map<String, String> constants = constPropPar.getConstants();

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

        if (constPropPar.toRemoveAssigned()) changes = visit(node.getChildren().get(0), constPropPar);

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

    private Boolean dealWithWhile(JmmNode node, ConstPropParameters constPropPar) {
        boolean changes = false;

        JmmNode conditional_node = node.getJmmChild(0);
        JmmNode expression_node = node.getJmmChild(1);

        if (constPropPar.toRemoveAssigned()) {
            changes = visit(expression_node, constPropPar);

        } else {
            ConstPropParameters constPropagationParamCopy = new ConstPropParameters(constPropPar);

            // 1st remove all that are assigned inside the scope
            constPropPar.setToRemoveAssigned(true);
            changes = visit(expression_node, constPropPar);
            constPropPar.setToRemoveAssigned(false);

            changes = visit(conditional_node, constPropPar) || changes;
            changes = visit(expression_node, constPropPar) || changes;

            ConstPropVisitor.intersectMaps(
                    constPropPar.getConstants(),
                    constPropPar.getConstants(),
                    constPropagationParamCopy.getConstants()
            );
        }

        return changes;
    }

    private static void intersectMaps(Map<String, String> res, Map<String, String> map1, Map<String, String> map2) {
        Map<String, String> mapFiltered = map2.entrySet().stream().filter(map -> {
            if (map1.containsKey(map.getKey())) {
                String val = map1.get(map.getKey());
                return val.equals(map.getValue());
            } else {
                return false;
            }
        }).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        res.clear();
        res.putAll(mapFiltered);
    }

    @Override
    protected void buildVisitor() {

    }
}
