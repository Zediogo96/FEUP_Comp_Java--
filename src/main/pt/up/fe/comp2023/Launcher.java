package pt.up.fe.comp2023;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp2023.jmm.jasmin.JasminBuilder;
import pt.up.fe.comp2023.ollir.JmmOptimizer;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

        TestUtils.noErrors(semanticsResult);

        System.out.println(parserResult.getRootNode().toTree());

        System.out.println(semanticsResult.getSymbolTable().toString());

        /* OLLIR */

        var optimizer = new JmmOptimizer();

        // Optimization stage
        if (config.get("optimize") != null && config.get("optimize").equals("true")) {
            semanticsResult = optimizer.optimize(semanticsResult);
        }

        System.out.println(config.get("optimize"));

        var ollirResult = optimizer.toOllir(semanticsResult);

        TestUtils.noErrors(ollirResult);

        /* JASMIN */

        var jasminBuilder = new JasminBuilder();

        var jasminResult = jasminBuilder.toJasmin(ollirResult);

        TestUtils.noErrors(jasminResult);

        jasminResult.compile();

        jasminResult.run();

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
        config.put("optimize", "true");
        config.put("registerAllocation", "-1");
        config.put("debug", "false");

        return config;
    }

}