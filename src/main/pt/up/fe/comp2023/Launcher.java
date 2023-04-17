package pt.up.fe.comp2023;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Launcher {

    public static void main(String[] args) {
        // Setups console logging and other things
        SpecsSystem.programStandardInit();

        // Parse arguments as a map with predefined options
        var config = parseArgs(args);

        // Get input file
        File inputFile = new File(config.get("inputFile"));

        // Check if file exists
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + inputFile + "'.");
        }

        // Read contents of input file
        String code = SpecsIo.read(inputFile);

        // Instantiate JmmParser
        SimpleParser parser = new SimpleParser();

        // Parse stage
        JmmParserResult parserResult = parser.parse(code, config);

        // Check if there are parsing errors
        TestUtils.noErrors(parserResult.getReports());

        /* SEMANTIC ANALYSIS STAGE */
        AnalysisStage analysisStage = new AnalysisStage();

        JmmSemanticsResult semanticsResult = analysisStage.semanticAnalysis(parserResult);

        /* CONVERTS ALL ELEMENTS TO STRING, READY TO BE MORE EASILY PRINTED */
        List<String> reports = semanticsResult.getReports().stream().map(Report::toString).toList();

        /* REMOVE DUPLICATE STRINGS FROM REPORTS */
        reports = reports.stream().distinct().collect(Collectors.toList());

        if (reports.size() > 0) {
            System.out.println("\nSemantic Analysis failed with " + reports.size() + " error(s):\n");
            for (var report : reports) {
                System.out.println("\t- " + report);
            }
            System.out.println("\n");

            throw new RuntimeException("Semantic analysis failed.");
        }


        /* END OF SEMANTIC ANALYSIS */

        System.out.println(parserResult.getRootNode().toTree());

        System.out.println(semanticsResult.getSymbolTable().toString());

 /*       String ollirCode = SpecsIo.read("test/pt/up/fe/comp/cp2/apps/example_ollir/Simple.ollir");
        OllirResult ollirResult = new OllirResult(ollirCode, Collections.emptyMap());
        JasminBuilder jasminBuilder = new JasminBuilder();
        JasminResult jasminResult = jasminBuilder.toJasmin(ollirResult);

        TestUtils.noErrors(jasminResult);*/

    }

    private static Map<String, String> parseArgs(String[] args) {
        SpecsLogs.info("Executing with args: " + Arrays.toString(args));

        // Check if there is at least one argument
        if (args.length != 1) {
            throw new RuntimeException("Expected a single argument, a path to an existing input file.");
        }

        // Create config
        Map<String, String> config = new HashMap<>();
        config.put("inputFile", args[0]);
        config.put("optimize", "false");
        config.put("registerAllocation", "-1");
        config.put("debug", "false");

        return config;
    }

}
