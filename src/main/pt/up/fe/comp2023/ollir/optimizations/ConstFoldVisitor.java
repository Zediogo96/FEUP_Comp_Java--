package pt.up.fe.comp2023.ollir.optimizations;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

public class ConstFoldVisitor extends PreorderJmmVisitor<String, Boolean> {

    public ConstFoldVisitor() {
        super();
        setDefaultVisit(this::defaultVisit);
        addVisit("BinaryOp", this::binOpVisit);
        addVisit("RelationalOp", this::relOpVisit);
    }

    @Override
    protected void buildVisitor() {

    }

    private Boolean relOpVisit(JmmNode node, String space) {
        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        boolean changes = visit(left);
        changes = visit(right) || changes;

        boolean hasBoolOperands = left.getKind().equals("Boolean") && right.getKind().equals("Boolean");
        boolean hasIntOperands = left.getKind().equals("Integer") && right.getKind().equals("Integer");

        if (hasBoolOperands) {
            boolean leftValue = Boolean.parseBoolean(left.get("value"));
            boolean rightValue = Boolean.parseBoolean(right.get("value"));

            boolean result = false;

            switch (node.get("op")) {
                case "&&" -> result = leftValue && rightValue;
                case "||" -> result = leftValue || rightValue;
            }

            JmmNode newNode = new JmmNodeImpl("Boolean");
            newNode.put("value", String.valueOf(result));

            node.replace(newNode);

            return true;
        }
        // Meaning deal with <, >, <=, >=, ==, and !=
        else if (hasIntOperands) {
            int leftValue = Integer.parseInt(left.get("value"));
            int rightValue = Integer.parseInt(right.get("value"));

            boolean result = false;

            switch (node.get("op")) {
                case "<" -> result = leftValue < rightValue;
                case ">" -> result = leftValue > rightValue;
                case "<=" -> result = leftValue <= rightValue;
                case ">=" -> result = leftValue >= rightValue;
                case "==" -> result = leftValue == rightValue;
                case "!=" -> result = leftValue != rightValue;
            }

            JmmNode newNode = new JmmNodeImpl("Boolean");
            newNode.put("value", String.valueOf(result));

            node.replace(newNode);
        }
        return changes;
    }

    private Boolean binOpVisit(JmmNode node, String space) {

        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        boolean changes = visit(left);
        changes = visit(right) || changes;

        boolean hasIntOperands = left.getKind().equals("Integer") && right.getKind().equals("Integer");

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

            return true;
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
