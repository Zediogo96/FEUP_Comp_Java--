package pt.up.fe.comp2023.ollir;

//import jmmSemanticsResult from ..

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2023.Analysis.MySymbolTable;
import pt.up.fe.comp2023.ollir.optimizations.ConstFoldVisitor;
import pt.up.fe.comp2023.ollir.optimizations.ConstPropParameters;
import pt.up.fe.comp2023.ollir.optimizations.ConstPropVisitor;

import java.util.Collections;


public class JmmOptimizer implements JmmOptimization {

    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {

        boolean optimize = semanticsResult.getConfig().get("optimize") != null
                && semanticsResult.getConfig().get("optimize").equals("true");

        if (!optimize) {
            System.out.println("Optimization disabled");
            return semanticsResult;
        }

        System.out.println("Optimization enabled");

        boolean changed = true;

        while (changed) {
            ConstPropVisitor constPropVisitor = new ConstPropVisitor();

            ConstPropParameters constPropPar = new ConstPropParameters();

            changed = constPropVisitor.visit(semanticsResult.getRootNode(), constPropPar);

            ConstFoldVisitor constFoldVisitor = new ConstFoldVisitor();

            changed = constFoldVisitor.visit(semanticsResult.getRootNode());
        }

        return semanticsResult;
    }

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        boolean optimize = semanticsResult.getConfig().get("optimize") != null
                && semanticsResult.getConfig().get("optimize").equals("true");

        System.out.println("OLLIR code output...");

        var ollirGenerator = new OllirGenerator((MySymbolTable) semanticsResult.getSymbolTable(), optimize);
        ollirGenerator.visit(semanticsResult.getRootNode());

        var ollirCode = ollirGenerator.getOllirCode();

        System.out.println(ollirCode);


        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

}






