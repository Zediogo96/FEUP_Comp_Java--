package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.Analysis.JmmSemanticAnalyser;
import pt.up.fe.comp2023.Analysis.MySymbolTable;
import pt.up.fe.comp2023.Analysis.SymbolTableVisitor;

import java.util.ArrayList;
import java.util.List;

public class AnalysisStage implements JmmAnalysis {

    private MySymbolTable st;

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {

        JmmNode curr_node = parserResult.getRootNode();

        MySymbolTable st = new MySymbolTable();
        List<Report> reports = new ArrayList<>();

        SymbolTableVisitor visitor = new SymbolTableVisitor(st, reports);
        visitor.visit(curr_node, "");

//      VISITOR FOR SEMANTIC ANALYSIS
        JmmSemanticAnalyser semanticAnalyser = new JmmSemanticAnalyser(st, reports);
        semanticAnalyser.visit(curr_node, null);
        System.out.println("\nSemantic Analysis Finished\n");

        return new JmmSemanticsResult(parserResult, st, reports);

    }

    public MySymbolTable getSymbolTable() {
        return st;
    }

}
