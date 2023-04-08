package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

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

        addVisit("Variable", this::dealWithVariable);

        addVisit("ArrayAccess", this::visitArrayAccess);
        addVisit("ArrayInit", this::dealWithArrayInit);


        addVisit("Integer", this::dealWithPrimitive);
        addVisit("Boolean", this::dealWithPrimitive);

        setDefaultVisit(this::defaultVisit);
    }

    private Map.Entry<String, String> dealWithClassDeclaration(JmmNode node, Boolean data) {
        currentSCOPE = "CLASS";
        return Map.entry("class", "true");
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
            field = Map.entry(st.getField(node.get("id")), false);
        } else if (currentSCOPE.equals("METHOD")) {
            field = Map.entry(currentMethod.getLocalVariable(node.get("id")), false);
        }
        if (field == null) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(node.get("lineStart")), Integer.parseInt(node.get("colStart")), "Variable not found: " + node.get("id")));
            return Map.entry("error", "null");
        }
        else {
            return Map.entry(field.getKey().getType().getName(), "true");
        }
    }


    private Map.Entry<String, String> visitArrayAccess(JmmNode node, Boolean data) {

        Map.Entry<String, String> dataReturn = Map.entry("int", "null");

        JmmNode array = node.getChildren().get(0);
        JmmNode index = node.getChildren().get(1);

        Map.Entry<String, String> indexReturn = visit(index, true);
        System.out.println(indexReturn.getValue());

        Symbol arraySymbol = st.getField(array.get("id"));

        if (arraySymbol == null) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(array.get("lineStart")), Integer.parseInt(array.get("colStart")), "Array not found: " + array.get("name")));
            return Map.entry("error", "null");
        } else if (!arraySymbol.getType().getName().equals("int[]")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(array.get("lineStart")), Integer.parseInt(array.get("colStart")), "Variable is not an array: " + array.get("id")));
            return Map.entry("error", "null");
        } else if (!indexReturn.getKey().equals("int")) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(index.get("lineStart")), Integer.parseInt(index.get("colStart")), "Array index is not an Integer: " + index));
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

