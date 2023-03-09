package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static pt.up.fe.comp2023.Analysis.MySymbolTable.getTypeFromAttr;
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
<<<<<<< HEAD
        addVisit("MethodDeclaration", this::dealWithMethodDeclaration);
=======
>>>>>>> 61c10a4020c0840c5d56dc6172a1645ee8acfca2
//
        setDefaultVisit(this::defaultVisit);
    }

    private String dealWithImport(JmmNode node, String space) {
        st.addImport(node.get("name"));
        return space + "IMPORT";
    }


    private String dealWithClassDeclaration(JmmNode node, String space) {
        st.setClassName(node.get("name"));

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

        Type returnType = getTypeFromAttr(node, "returnType");
        st.addMethod(node.get("name"), returnType, parametersList);
        return space + "METHOD";


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
        addVisit("ImportDeclaration", this::dealWithImport);

    }
}
