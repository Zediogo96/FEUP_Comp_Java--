package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;

import java.util.List;
import java.util.stream.Collectors;

public class SymbolTableVisitor extends PreorderJmmVisitor<String, String> {

    private final MySymbolTable st;

    private String scope;
    private final List<Report> reports;

    public SymbolTableVisitor(MySymbolTable st, List<Report> reports) {
        this.st = st;
        this.reports = reports;

        addVisit("ImportDeclaration", this::dealWithImport);
        addVisit("ClassDeclaration", this::dealWithClassDeclaration);
//
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
