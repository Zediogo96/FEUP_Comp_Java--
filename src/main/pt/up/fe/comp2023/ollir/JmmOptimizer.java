package pt.up.fe.comp2023.ollir;

//import jmmSemanticsResult from ..

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2023.Analysis.MySymbolTable;

import java.util.Collections;


public class JmmOptimizer implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        boolean optimize = semanticsResult.getConfig().get("optimize") != null
                && semanticsResult.getConfig().get("optimize").equals("true");

        System.out.println("Generating OLLIR code...");

        var ollirGenerator = new OllirGenerator((MySymbolTable) semanticsResult.getSymbolTable(), optimize);
        ollirGenerator.visit(semanticsResult.getRootNode());

        var ollirCode = ollirGenerator.getOllirCode();

        if (semanticsResult.getConfig().get("debug") != null
                && semanticsResult.getConfig().get("debug").equals("true")) {
            System.out.println(ollirCode);
        }

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

}






