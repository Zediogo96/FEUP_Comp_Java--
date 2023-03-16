package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static pt.up.fe.comp2023.Analysis.MySymbolTable.getTypeFromNode;

public class SymbolTableVisitor extends PreorderJmmVisitor<String, String> {

    private final MySymbolTable st;

    private String scope;
    private final List<Report> reports;

    public SymbolTableVisitor(MySymbolTable st, List<Report> reports) {
        this.st = st;
        this.reports = reports;

        addVisit("ImportDeclaration", this::dealWithImport);
        addVisit("ClassDeclaration", this::dealWithClassDeclaration);
        addVisit("MainMethodDeclaration", this::dealWithMainMethodDeclaration);
        addVisit("MethodDeclaration", this::dealWithMethodDeclaration);
        addVisit("VarDeclaration", this::dealWithVarDeclaration);

        setDefaultVisit(this::defaultVisit);
    }

    private String dealWithImport(JmmNode node, String space) {
        st.addImport(node.get("name"));
        return space + "IMPORT";
    }


    private String dealWithClassDeclaration(JmmNode node, String space) {
        st.setClassName(node.get("className"));
        try {
            st.setSuperClassName(node.get("extendName"));
        } catch (NullPointerException ignored) {

        }
        scope = "CLASS";
        return space + "CLASS";
    }

    private String dealWithMethodDeclaration(JmmNode node, String space) {
        scope = "METHOD";
//        get all children of node that contains Parameter
        List<JmmNode> parameters = node.getChildren().stream().filter(n -> n.getKind().equals("Parameter")).toList();
        List<Symbol> parametersList = new ArrayList<>();
        for (JmmNode parameter : parameters) {
            JmmNode typeNode = parameter.getChildren().get(0);
            parametersList.add(new Symbol(getTypeFromNode(typeNode), parameter.get("name")));
        }

        JmmNode typeNode = node.getChildren().get(0);
        st.addMethod(node.get("name"), getTypeFromNode(typeNode), parametersList);

        return space + "METHOD";
    }

    private String dealWithMainMethodDeclaration(JmmNode node, String space) {
        scope = "MAIN";
        var parametersList = new ArrayList<Symbol>();
        parametersList.add(new Symbol(new Type("String[]", true), "args"));
        st.addMethod("main", new Type("void", false), parametersList);

        return space + "MAIN";
    }

    private String dealWithVarDeclaration(JmmNode node, String space) {
        JmmNode typeNode = node.getChildren().get(0);
        Symbol field = new Symbol(getTypeFromNode(typeNode), node.get("name"));

        if (scope.equals("CLASS")) {
            st.addField(field, false);
        } else if (scope.equals("METHOD")) {
            st.getCurrentMethod().addLocalVariable(field, false);
        }

        return space + "VAR";
    }

    /**
     * Prints node information and appends space
     *
     * @param node  Node to be visited
     * @param space Info passed down from other nodes
     * @return New info to be returned
     */
    private String defaultVisit(JmmNode node, String space) {
        String content = space + node.getKind();
        String attrs = node.getAttributes()
                .stream()
                .filter(a -> !a.equals("line"))
                .map(a -> a + "=" + node.get(a))
                .collect(Collectors.joining(", ", "[", "]"));

        content += ((attrs.length() > 2) ? attrs : "");

        return content;
    }

    @Override
    protected void buildVisitor() {
    }
}
