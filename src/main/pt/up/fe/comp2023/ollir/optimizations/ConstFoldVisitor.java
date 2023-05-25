package pt.up.fe.comp2023.ollir.optimizations;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

public class ConstFoldVisitor extends AJmmVisitor<String, Boolean> {

    public ConstFoldVisitor() {
        super();
        setDefaultVisit(this::defaultVisit);
        addVisit("BinaryOp", this::binOpVisit);
    }

    @Override
    protected void buildVisitor() {

    }

    private Boolean binOpVisit(JmmNode node, String space) {

        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        boolean changes = visit(left);
        changes = visit(right) || changes;

        boolean hasIntOperands = left.getKind().equals("Integer") && right.getKind().equals("Integer");
        System.out.println("hasIntOperands: " + hasIntOperands);

        if (hasIntOperands) {
            int leftValue = Integer.parseInt(left.get("value"));
            int rightValue = Integer.parseInt(right.get("value"));

            int result = 0;

            switch (node.get("op")) {
                case "+" -> result = leftValue + rightValue;
                case "-" -> result = leftValue - rightValue;
                case "*" -> result = leftValue * rightValue;
                case "/" -> result = leftValue / rightValue;
            }

            JmmNode newNode = new JmmNodeImpl("Integer");
            newNode.put("value", String.valueOf(result));

            node.replace(newNode);

            return false;
        }
        return changes;
    }

    public Boolean defaultVisit(JmmNode jmmNode, String dummy) {
        boolean changes = false;

        for (JmmNode child : jmmNode.getChildren()) {
            changes = visit(child) || changes;
        }

        return changes;
    }
}
