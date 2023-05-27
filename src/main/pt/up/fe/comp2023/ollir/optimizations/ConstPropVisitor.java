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

        System.out.println("CONSTANTS: " + constants);
        System.out.println("NAME: " + name);

        if (constants.containsKey(name)) {
            JmmNode newNode;

            switch (constants.get(name)) {
                case "true", "false" -> newNode = new JmmNodeImpl("Boolean");
                default -> newNode = new JmmNodeImpl("Integer");
            }
            newNode.put("value", constants.get(name));
            node.replace(newNode);
            System.out.println("NEW NODE 1: " + newNode);
            return true;
        }
        return false;
    }

    public boolean dealWithAssignment(JmmNode node, ConstPropParameters constPropPar) {
        boolean changes = false;

        boolean assignmentIsArrayVariable = node.getChildren().get(0).getKind().equals("ArrayAccess");

        if (constPropPar.toRemoveAssigned()) {
            System.out.println("NODE: " + node);
            System.out.println("NODE CHILDREN: " + node.getChildren());
            changes = visit(node.getChildren().get(0), constPropPar) || changes;
        }

        if (assignmentIsArrayVariable) return false;

        String assigneeName = node.get("id");
        System.out.println("ASSIGNEE NAME: " + assigneeName);

        constPropPar.getConstants().remove(assigneeName);

        if (!constPropPar.toRemoveAssigned()) {

            JmmNode newNode = node.getChildren().get(0);
            switch (newNode.getKind()) {
                case "Integer", "Boolean" -> {
                    constPropPar.getConstants().put(assigneeName, newNode.get("value"));
                    System.out.println("CONSTANTS: " + constPropPar.getConstants());
                }
            }
        }

        return changes;
    }

    private Boolean dealWithWhile(JmmNode node, ConstPropParameters constPropagationParam) {
        boolean changes = false;

        JmmNode condNode = node.getJmmChild(0);
        JmmNode scopeNode = node.getJmmChild(1);

        if (constPropagationParam.toRemoveAssigned()) {
            changes = visit(scopeNode, constPropagationParam) || changes;

        } else {
            ConstPropParameters constPropagationParamCopy = new ConstPropParameters(constPropagationParam);

            // 1st remove all that are assigned inside the scope
            constPropagationParam.setToRemoveAssigned(true);
            changes = visit(scopeNode, constPropagationParam) || changes;
            constPropagationParam.setToRemoveAssigned(false);

            changes = visit(condNode, constPropagationParam) || changes;
            changes = visit(scopeNode, constPropagationParam) || changes;

            ConstPropVisitor.intersectMaps(
                    constPropagationParam.getConstants(),
                    constPropagationParam.getConstants(),
                    constPropagationParamCopy.getConstants()
            );
        }

        return changes;
    }

    @Override
    protected void buildVisitor() {

    }
}
