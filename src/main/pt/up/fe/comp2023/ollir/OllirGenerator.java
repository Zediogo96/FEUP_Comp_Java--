package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp2023.Analysis.MySymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.*;
import java.util.stream.Collectors;


public class OllirGenerator extends AJmmVisitor <OllirInference, String> {

    private final StringBuilder ollirCode;
    private final MySymbolTable st;
    private final boolean optimize;

    private int indent;
    private int tempVarCount;
    private int ifThenElseCount;
    private int whileCount;

    private String currentSCOPE;

    private String currentMETHOD;

    private Map<String, Integer> parameterIndex = new HashMap<>();

    @Override
    protected void buildVisitor() {
        addVisit("Start", this::startVisit);
        addVisit("Program", this::visitProgram);
        addVisit("ClassDeclaration", this::visitClassDeclaration);
        addVisit("MainMethodDeclaration", this::visitMethodDeclaration);
        addVisit("MethodDeclaration", this::visitMethodDeclaration);
        //addVisit("Parameter", this::visitParameter);
        addVisit("VarDeclaration", this::visitVarDeclaration);
        addVisit("Assignment", this::visitAssignment);
        addVisit("Variable", this::visitVariable);
        addVisit("Stmt", this::visitStmt);
        addVisit("Expr", this::visitExpression);
        addVisit("ReturnStmt", this::visitReturn);
        addVisit("Ret", this::visitExpression);
        addVisit("Identifier", this::visitExpression);
        addVisit("Integer", this::integerVisit);
        addVisit("Boolean", this::boolVisit);
        addVisit("BinaryOp", this::visitBinaryOperator);
        addVisit("This", this::visitThis);
        addVisit("AccessMethod", this::visitAccessMethod);
        addVisit("MethodCall", this::visitMethodCall);
        addVisit("RelationalOp", this::visitRelationalOperator);
        addVisit("NewObject", this::visitNewObject);

        setDefaultVisit((node, dummy) -> null);
    }

    //TODO: visit assignments and arithmetic operations (with correct precedence)

    public OllirGenerator(MySymbolTable st, boolean optimize) {
        this.ollirCode = new StringBuilder();
        this.st = st;
        this.optimize = optimize;
        this.indent = 0;
        this.tempVarCount = 0;
        //more things
    }

    private void addIndent() {
        this.indent++;
    }

    private void removeIndent() {
        this.indent--;
    }

    private String getIndent() {
        return "\t".repeat(Math.max(0, this.indent));
    }

    private int getAndAddTempVarCount(JmmNode node) {
        String currentMethod = getCurrentMethodName(node);
        Set<String> methodVars = getMethodVars(node);

        while(true) {
            this.tempVarCount++;
            String tempVarName = "t" + this.tempVarCount;
            if (!methodVars.contains(tempVarName)) {
                return this.tempVarCount;
            }
        }
    }

private int getTempVarCount() {
        return this.tempVarCount;
    }

    private Set<String> getMethodVars(JmmNode node) {
        String currentMethod = getCurrentMethodName(node);
        Set<String> methodVars = new HashSet<>();
        for (var child : node.getChildren()) {
            if (child.getKind().equals("varDeclaration")) {
                methodVars.add(child.get("name"));
            }
        }
        return methodVars;
    }

    public String getOllirCode() {
        return this.ollirCode.toString();
    }

    private String startVisit(JmmNode startNode, OllirInference inference) {
        visit(startNode.getChildren().get(0));
        return "";
    }

    private String visitProgram(JmmNode programNode, OllirInference inference) {
        //System.out.println("IMPORTS:" + st.getImports());

        List<String> sublists = new ArrayList<String>();

        for (var importStr : st.getImports()) {
            //System.out.println("IMPORTS SUBLIST:" + importStr);
            sublists.add(importStr);
        }

        for (var importLower : sublists) {
            String intstr = importLower.replaceAll("[]]", "");
            String outstr = intstr.replaceAll("\\[", "");
            String importstr = outstr.replaceAll(", ", ".");

            ollirCode.append("import ").append(importstr).append(";").append("\n");
        }

        ollirCode.append("\n");

        for (var child : programNode.getChildren()) {
            visit(child);
        }

        return "";
    }

    private String visitClassDeclaration(JmmNode classDeclarationNode, OllirInference inference) {
        currentSCOPE = "CLASS";
        //init

        ollirCode.append(getIndent()).append("public ").append(st.getClassName());
        var superClassName = st.getSuper();
        System.out.println("SUPERCLASSNAME: " + superClassName);
        if (superClassName != null && !superClassName.equals("Object")) {
            ollirCode.append(" extends ").append(superClassName);
        }
        ollirCode.append(" {\n");
        this.addIndent();

        //fields

        for (var field : st.getFields()) {
            ollirCode.append(getIndent()).append(".field ").append(field.getName()).append(OllirUtils.getOllirType(field.getType())).append(";\n");
        }

        //default constructor

        ollirCode.append(getIndent()).append(".construct ").append(st.getClassName()).append("().V {\n");
        this.addIndent();
        ollirCode.append(getIndent()).append("invokespecial(this, \"<init>\").V;\n");
        this.removeIndent();
        ollirCode.append(getIndent()).append("}\n");

        //children

        for (var child : classDeclarationNode.getChildren()) {
            visit(child);
        }

        this.removeIndent();

        ollirCode.append(getIndent()).append("}\n");

        return "";
    }


    private String visitMethodDeclaration(JmmNode methodDecl, OllirInference inference) {
        currentSCOPE = "METHOD";
        var methodName = "";
        List<JmmNode> statements;
        List<JmmNode> methodDeclChildren = null;

        boolean isMain = methodDecl.getKind().equals("MainMethodDeclaration");

        if (isMain) {
            methodName = "main";

            ollirCode.append(getIndent()).append(".method public static ").append(methodName).append("(");

            methodDeclChildren = methodDecl.getChildren();
            //System.out.println("MAIN METHOD DECLARATION CHILDREN: " + methodDeclChildren);

        } else {
            // First child of MethodDeclaration is MethodHeader

            methodName = methodDecl.get("name");

            ollirCode.append(getIndent()).append(".method public ").append(methodName).append("(");

            methodDeclChildren = methodDecl.getChildren();
            //System.out.println("METHOD DECLARATION CHILDREN: " + methodDeclChildren);
        }

        //parameters

        var params = st.getParameters(methodName);

        var param_index = 0;

        //for each param, get save the name and index

        if (!params.isEmpty()) {
            for (var param : params) {
                parameterIndex.put(param.getName(), param_index);
                param_index++;
            }
        }

        //System.out.println("PARAMS: " + params);

        var paramCode = params.stream()
                .map(OllirUtils::getCode).
                collect(Collectors.joining(", "));

        //System.out.println("PARAM CODE: " + paramCode);
        ollirCode.append(paramCode).append(")");

        ollirCode.append(OllirUtils.getOllirType(st.getReturnType(methodName)));

        ollirCode.append(" {\n");

        this.addIndent();

        int counter = 0;

        //children (includes return statement at the end)

        for (var child : methodDeclChildren) {
            counter++;
            System.out.println("Child of method declaration number: " + counter + " is: " + child);
            visit(child);
        }

        this.removeIndent();

        ollirCode.append(getIndent()).append("}\n");

        return "";
    }

    private String visitAssignment(JmmNode assignmentNode, OllirInference inference) {

        //System.out.println("VISITING ASSIGNMENT NODE: " + assignmentNode);
        //System.out.println("VISITING ASSIGNMENT NODE CHILDREN: " + assignmentNode.getChildren());

        String toAssign = assignmentNode.get("id");
        boolean isField = false;
        var toAssignSymbol = st.getLocalVariableFromMethod(getCurrentMethodName(assignmentNode), toAssign);
        if (toAssignSymbol == null) {
            toAssignSymbol = st.getField(toAssign).getKey();
            isField = true;
            System.out.println("TO ASSIGN SYMBOL: " + toAssignSymbol);
        }
        var toAssignType = OllirUtils.getOllirType(toAssignSymbol.getType());

        String type = "";

        if (assignmentNode.getJmmChild(0).getKind().equals("Integer")) {
            type = ".i32";
        }
        else if(assignmentNode.getJmmChild(0).getKind().equals("Boolean")) {
            type = ".bool";
        }
        else if (assignmentNode.getJmmChild(0).getKind().equals("Variable")) {

            String varid = assignmentNode.getJmmChild(0).get("id");
            //System.out.println("VAR ID: " + varid);
            var localvars = st.getLocalVariables(getCurrentMethodName(assignmentNode));
            //System.out.println("LOCAL VARS: " + localvars);
            var params = st.getParameters(getCurrentMethodName(assignmentNode));
            //System.out.println("PARAMS: " + params);

            Symbol varSymbol = null;
            for (var localvar : localvars) {
                if (localvar.getName().equals(varid)) {
                    varSymbol = localvar;
                }
            }
            if (varSymbol == null) {
                for (var param : params) {
                    if (param.getName().equals(varid)) {
                        varSymbol = param;
                    }
                }
            }
            if (varSymbol == null) {
                varSymbol = st.getField(varid).getKey();  //in case of being a field
                System.out.println("VAR SYMBOL IS FIELD: " + varSymbol);
                var newTemp = getAndAddTempVarCount(assignmentNode);
                var varType = OllirUtils.getOllirType(varSymbol.getType());
                ollirCode.append(getIndent()).append("t").append(newTemp).append(varType).append(" :=").append(varType).append(" getfield(this, ").append(varSymbol.getName()).append(varType).append(")").append(OllirUtils.getOllirType(varSymbol.getType())).append(";\n");
            }
            //System.out.println("VAR SYMBOL: " + varSymbol);
            type = OllirUtils.getOllirType(varSymbol.getType());
        }

        else if (assignmentNode.getJmmChild(0).getKind().equals("BinaryOp")) {

            String retstr = visit(assignmentNode.getJmmChild(0));
            ollirCode.append(getIndent()).append(toAssign).append(toAssignType).append(" :=").append(toAssignType).append(" ").append(retstr).append(";\n");
            return retstr;
        }

        else if (assignmentNode.getJmmChild(0).getKind().equals("NewObject")) {

            type = toAssignType;

        }

        else if (assignmentNode.getJmmChild(0).getKind().equals("AccessMethod")){
            type = toAssignType;
        }

        else if (assignmentNode.getJmmChild(0).getKind().equals("MethodCall")){
            type = toAssignType;
        }

        else if (assignmentNode.getJmmChild(0).getKind().equals("This")){
            type = toAssignType;
        }

        if (isField) {
            ollirCode.append(getIndent()).append("putfield(this, ").append(toAssign).append(toAssignType).append(", ");
        }
        else {
            ollirCode.append(getIndent()).append(toAssign).append(toAssignType).append(" :=").append(type).append(" ");
        }
        //ollirCode.append(getIndent()).append(toAssign).append(toAssignType).append(" :=").append(type).append(" ");  //.append(str_assigned).append(";\n");  //append generally the assignment  structure, then each visitor will append the rhs

        String str_assigned;

        for (JmmNode child : assignmentNode.getChildren()) {
            //System.out.println("VISITING CHILD OF ASSIGNMENT NODE: " + child);
            String returnstr = visit(child);
            //System.out.println("RETURN STR: " + returnstr);
            if (!child.getKind().equals("NewObject")) {
                ollirCode.append(returnstr);
            }
            //System.out.println("OLLIR CODE AFTER VISITING CHILD OF ASSIGNMENT NODE: " + ollirCode);
        }

        if (isField) {
            ollirCode.append(").V;\n");
        }
        else {
            ollirCode.append(";\n");
        }

        //ollirCode.append(";\n");

        if (assignmentNode.getJmmChild(0).getKind().equals("NewObject")) {
            ollirCode.append(getIndent()).append("invokespecial(").append(toAssign + toAssignType).append(", \"<init>\")").append(".V");
            ollirCode.append(";\n");
        }

        return "";
    }

    private String visitVariable(JmmNode variableNode, OllirInference inference) {
        String varName = variableNode.get("id");
        String currentMethod = getCurrentMethodName(variableNode);
        String varType = "";

        if (currentSCOPE.equals("CLASS")) {
            var field = st.getField(varName);
            var varTypeInt = field.getKey().getType();
            varType = OllirUtils.getOllirType(varTypeInt);
        }
        else if(currentSCOPE.equals("METHOD") && currentMethod != null) {
            var _params = st.getParameters(currentMethod);
            //System.out.println("PARAMS: " + _params);
            var variable = st.getLocalVariableFromMethod(varName, currentMethod);
            var variables = st.getLocalVariables(currentMethod);
            for (var v : variables) {
                if (v.getName().equals(varName)) {
                    variable = v;
                }
            }

            //var varTypeInt = variable.getType();
            //varType = OllirUtils.getOllirType(varTypeInt);

            if (variable == null) {
                var parameters_var = st.getField(varName);
                //System.out.println("PARAMETERS VAR: " + parameters_var);
                var varTypeInt = parameters_var.getKey().getType();
                varType = OllirUtils.getOllirType(varTypeInt);
            } else {
                var varTypeInt = variable.getType();
                varType = OllirUtils.getOllirType(varTypeInt);
            }

        }

        //System.out.println("DEBUGGING RETURN NODE: " + returnNode);

        //System.out.println("DEBUGGING RETURN NODE CHILD: " + exprNode);


        String param_indexstring = "";
        boolean varIsParam = false;

            if (parameterIndex != null) {
                var param_index = parameterIndex.get(variableNode.get("id"));
                if (param_index != null) {
                    param_index += 1;
                    param_indexstring = "$" + param_index.toString() + ".";
                    varIsParam = true;
                }
            }

        var varField = st.getField(varName);
        boolean varIsField = false;
        var isAssignChild = variableNode.getAncestor("Assignment").isPresent();

        if (varField != null) {
            varIsField = true;
        }

        //ollirCode.append(varName).append(varType);

        String str;

        if (varIsParam) {
            str = param_indexstring + varName + varType;
        } else if (varIsField) {
            //str = "getfield(this, " + varName + varType + ")" + varType;
            var currentTemp = getTempVarCount();
            str = "t" + currentTemp + varType;
        } else if (varIsField && isAssignChild) {
            var currentTemp = getTempVarCount();
            str = "t" + currentTemp + varType;
        } else {
            str = varName + varType;
        }

        return str;
    }

    private String visitStmt(JmmNode stmt, OllirInference inference) {
        //System.out.println("VISITING STMT" + stmt);

        for (var child : stmt.getChildren()) {
            var childreturn = visit(child);
            //if (childreturn == "") {System.out.println("CHILD RETURN IS EMPTY: VISITED EXPRESSION DERIVING FROM STMT ");}
            //System.out.println("CHILD RETURN: " + childreturn);
            //return childreturn;
        }

        return "";
    }

    private String visitExpression(JmmNode expression, OllirInference inference) {
        //System.out.println("VISITING EXPRESSION" + expression);

        var kind = expression.getKind();

        if (kind.equals("ReturnStmt")) {
            //System.out.println("RETURN STMT");
            var returnStmt = expression.getJmmChild(0);
            //System.out.println("RETURN STMT CHILD: " + returnStmt);
            visit(returnStmt);
        }

        if (kind.equals("Ret")) {
            //System.out.println("FINAL EXPRESSION THINGS: " + expression.getChildren());
            var returningtoret = visit(expression.getJmmChild(0));
            //System.out.println("RETURNING TO RET: " + returningtoret);
            return returningtoret;

        }

        if (kind.equals("Identifier")) {
            //System.out.println("IDENTIFIER");
            var identifier = expression.get("id");
            //System.out.println("IDENTIFIER NAME: " + identifier);
            return identifier;
        }

        for (var child : expression.getChildren()) {
            visit(child);
        }

        return "";
    }

    private String visitReturn(JmmNode returnNode, OllirInference inference) {

        //System.out.println("VISITING RETURN" + returnNode);

        String exprnodeReturn = "";
        boolean returnIsparam = false;

        //System.out.println("DEBUGGING RETURN NODE: " + returnNode);
        JmmNode exprNode = returnNode.getJmmChild(0);
        //System.out.println("DEBUGGING RETURN NODE CHILD: " + exprNode);
        if (exprNode == null) {
            //return "null";
            System.out.println("aaa");
        }
        else {

            //System.out.println("DEBUGGING RETURN NODE CHILD: " + exprNode);
            exprnodeReturn = visit(exprNode);
            //System.out.println("What is coming from expr node?: " + exprnodeReturn);

            String returnString = OllirUtils.getOllirType(st.getReturnType(getCurrentMethodName(returnNode))) + " ";

            String returnReg = exprnodeReturn;

            //System.out.println("DEBUUGING RETURN REGISTER: " + returnReg);

            ollirCode.append(getIndent()).append("ret").append(returnString)
                    .append(returnReg).append(";\n");

            return exprnodeReturn;
        }

        return "";
    }

    private String visitParameter(JmmNode parameterNode, OllirInference inference) {

        List <Symbol> methodParameters = st.getParameters(getCurrentMethodName(parameterNode));

        for (var child : parameterNode.getChildren()) {
            visit(child);
        }

        var paramCode = methodParameters.stream()
                .map(OllirUtils::getCode).
                collect(Collectors.joining(", "));

        ollirCode.append(paramCode).append(")");
        return "";
    }

    private String integerVisit(JmmNode integerNode, OllirInference inference) {
        //System.out.println("DEBUGGING INTEGER NODE"  + integerNode.get("value"));
        var value = integerNode.get("value");
        var str = value + ".i32";
        return str;
    }

    private String boolVisit(JmmNode boolNode, OllirInference inference) {
        //System.out.println("DEBUGGING BOOL NODE: " + boolNode.get("value"));
        var value = boolNode.get("value");  //TODO: check if this is correct with OllirUtils
        var str = value + ".bool";
        return str;
    }

    private String visitVarDecl(JmmNode varDeclNode, OllirInference inference) {
        //System.out.println("DEBUGGING INT NODE: " + varDeclNode);
        if (varDeclNode.getJmmChild(0).getKind().equals("IntType")) {
            ollirCode.append(integerVisit(varDeclNode.getJmmChild(0), inference));
//        } else if (varDeclNode.getJmmChild(0).getKind().equals("BoolType")) {
//            ollirCode.append(boolVisit(varDeclNode.getJmmChild(0), inference));
        } else {
            return "ERROR";
        }
        return "";
    }

    private String getCurrentMethodName(JmmNode node) {
        Optional<JmmNode> methodDeclarationNode = node.getAncestor("MethodDeclaration");
        if (methodDeclarationNode.isPresent()) {
            return methodDeclarationNode.get().get("name");
        }
        return "main";
    }

    private String visitVarDeclaration(JmmNode varDeclNode, OllirInference inference) {
        //System.out.println("DEBUGGING VAR DECLARATION NODE: " + varDeclNode);
        //System.out.println("DEBUGGING VAR DECLARATION NODE CHILDREN: " + varDeclNode.getChildren());

//        String varName = varDeclNode.get("name");
//        var varType = varDeclNode.getJmmChild(0);
//        //System.out.println("DEBUGGING VAR DECLARATION NODE TYPE: " + varType);
//        //String varTypeOllir = OllirUtils.getOllirType(varType);
//
//        var localvars = st.getLocalVariables(getCurrentMethodName(varDeclNode));
//        //System.out.println("DEBUGGING LOCAL VARS: " + localvars);
//
//        //get vartype of varname from localvars
//
//        for (Symbol s : localvars) {
//            if (s.getName().equals(varName)) {
//                //System.out.println("DEBUGGING VAR DECLARATION NODE TYPE: " + s.getType());
//                System.out.println("DEBUG VARDECL CHILDREN: " + varDeclNode.getChildren());
//                String varTypeOllir = OllirUtils.getOllirType(s.getType());
//                String varAssignDefault = OllirUtils.getDefaultVarDecl(s.getType());
//                //System.out.println("DEBUGGING VAR DECLARATION NODE TYPE: " + varTypeOllir);
//                ollirCode.append(getIndent()).append(varName).append(varTypeOllir).append(varAssignDefault).append(";\n");
//            }
//        }

        //TODO: This is commented because of var declaration walkover
        return "";
    }

    private String visitBinaryOperator(JmmNode binaryOperator, OllirInference inference) {
        //System.out.println("VISITING BINARY OPERATOR" + binaryOperator);

        //System.out.println("DEBUG OP" + binaryOperator.get("op"));

        var parentRet = binaryOperator.getAncestor("ReturnStmt").isPresent();
        //System.out.println("DEBUG PARENT RET" + parentRet);

        String op = binaryOperator.get("op");
        String opstring = OllirUtils.getOperator(op);
        var assignmentType = OllirUtils.getOperatorType(op);

        String left = visit(binaryOperator.getJmmChild(0), new OllirInference(assignmentType, true));
        //System.out.println("DEBUG LEFT" + binaryOperator.getJmmChild(0) + left);
        String right = visit(binaryOperator.getJmmChild(1), new OllirInference(assignmentType, true));
        //System.out.println("DEBUG RIGHT" + binaryOperator.getJmmChild(0) + right);

        String result = left + " " + opstring + " " + right;

        if (inference == null && !parentRet) {
            return result;
        }

        if (inference == null || inference.getIsAssignedToTempVar()) {
            int tempVar = getAndAddTempVarCount(binaryOperator);
            ollirCode.append(getIndent()).append("t").append(tempVar).append(assignmentType).append(" :=").append(assignmentType).append(" ").append(result).append(";\n");
            return "t" + tempVar + assignmentType;
        }

        return result;
    }

    private String visitRelationalOperator(JmmNode relationalOperator, OllirInference inference) {
        //System.out.println("VISITING BINARY OPERATOR" + binaryOperator);

        String op = relationalOperator.get("op");
        String opstring = OllirUtils.getOperator(op);

        String left = visit(relationalOperator.getJmmChild(0));
        String right = visit(relationalOperator.getJmmChild(1));;

        //System.out.println("Left: " + left);
        //System.out.println("Right: " + right);

        String result = left + " " + opstring + " " + right;

        return result;
    }




    private String visitAccessMethod(JmmNode methodCallNode, OllirInference inference) {

        //System.out.println("DEBUGGING METHODCALL NODE: " + methodCallNode);

        var externalMethodParams = st.getParameters(getCurrentMethodName(methodCallNode));

        //System.out.println("DEBUGGING EXTERNAL METHOD PARAMS: " + externalMethodParams);

        //System.out.println("CHILD = " + methodCallNode.getChildren());

        //method call like io.println(a)
        var parent = methodCallNode.getAncestor("Assignment");
        //System.out.println("DEBUGGING PARENT: " + parent);
//        System.out.println("DEBUGGING PARENT CHILDREN: " + parent.get().get("id"));
        Symbol toAssignSymbol = null;
        if (parent.isPresent()) {
            toAssignSymbol = st.getLocalVariableFromMethod(getCurrentMethodName(methodCallNode), parent.get().get("id"));
        }
        else {
            ollirCode.append(getIndent());
        }
//        toAssignSymbol = st.getLocalVariableFromMethod(getCurrentMethodName(methodCallNode), parent.get().get("id"));
        var toAssignType = "";
        if (toAssignSymbol == null) {
            toAssignType = "";
        }
        else {
            toAssignType = OllirUtils.getOllirType(toAssignSymbol.getType());
        }
//        toAssignType = OllirUtils.getOllirType(toAssignSymbol.getType());

        String firstArg = methodCallNode.getJmmChild(0).get("id");  //get the first identifier of the method call, like io in io.println(a)
        //System.out.println("DEBUGGING FIRST ARG: " + firstArg);
        String methodId = methodCallNode.getJmmChild(1).get("id");  //get the method identifier, like println in io.println(a)
        //System.out.println("DEBUGGING METHOD ID: " + methodId);
        //System.out.println("DEBUGGING METHOD ID" + methodId);
        //String methodName = methodCallNode.get("method");  //get the method name, like println in io.println(a)
        //for loop to visit the rest of the children

        String invokeType = OllirUtils.getInvokeType(firstArg, st);
        //String returnType = OllirUtils.getOllirType(st.getReturnType(getCurrentMethodName(methodCallNode))) + " ";
        String returnType = "";


        if (parent.isPresent()) {
            returnType = toAssignType;
        }
        else {
            returnType = ".V";
            //get the return type of the actual method
            //System.out.println("DEBUGGING METHOD RETURN TYPE: " + st.getReturnType(getCurrentMethodName(methodCallNode)));
        }

        //list of args
        List<String> argsList = new ArrayList<>();

        List<JmmNode> argsJmm = new ArrayList<>();

        var localvars = st.getLocalVariables(getCurrentMethodName(methodCallNode));

        var params = st.getParameters(getCurrentMethodName(methodCallNode));

        boolean addIndentToNewObject = false;

        for (int i = 2; i < methodCallNode.getChildren().size(); i++) {

            argsJmm.add(methodCallNode.getJmmChild(i));
            if (methodCallNode.getJmmChild(i).getKind().equals("NewObject")) {
                var tvar = visit(methodCallNode.getJmmChild(i));
                //System.out.println("DEBUGGING TVAR: " + tvar);
                argsList.add(tvar);
                addIndentToNewObject = true;
                //argsList.add(methodCallNode.getJmmChild(i).get("tvar"));
            }

        }

        //System.out.println("DEBUGGING ARGS JMM: " + argsJmm);

        Symbol firstArgNotImports = st.getLocalVariableFromMethod(getCurrentMethodName(methodCallNode), firstArg);

        StringBuilder operationString = new StringBuilder();

        //check if first arg is an object from the parameters

        for (var param : params) {
            if (param.getName().equals(firstArg)) {
                firstArgNotImports = param;
            }
        }

        if (firstArgNotImports != null) {
            operationString.append(invokeType + "(" + firstArg + OllirUtils.getOllirType(firstArgNotImports.getType()) + ", \"" + methodId + "\"");
        } else {
            if (addIndentToNewObject) {
                operationString.append(getIndent());
            }
            operationString.append(invokeType + "(" + firstArg + ", \"" + methodId + "\"");
        }

        //System.out.println("DEBUGGING STRING BUILDER: " + operationString);

        for (var arg : argsJmm) {
            if (arg.getKind().equals("Integer")){
                operationString.append(", ").append(arg.get("value")).append(".i32");
            }
            else if (arg.getKind().equals("Boolean")){
                operationString.append(", ").append(arg.get("value")).append(".bool");
            }
            else {
                //System.out.println("ENTERED INLINE CLASS");
                //System.out.println("DEBUGGING ARG TEMP: " + arg);
                //System.out.println("LOCAL VARS: " + localvars);
                //System.out.println("PARAMS: " + params);
                boolean found = false;
                //operationString.append(", ").append(arg.get("id"));
                for (var localvar : localvars) {
                    if (arg.get("id").equals(localvar.getName())){
                        String argType = OllirUtils.getOllirType(localvar.getType());
                        operationString.append(", ").append(localvar.getName()).append(argType);
                        found = true;
                        break;
                    }
//                    else {
//                        operationString.append(", ").append("t").append(getTempVarCount()).append(".").append(arg.get("id"));
//                    }
                }
                if (found) {continue;}
                for (var param : params) {
                    if (arg.get("id").equals(param.getName())){
                        //System.out.println("GOT PARAM C CORRECTLY");
                        String argType = OllirUtils.getOllirType(param.getType());
                        operationString.append(", ").append(param.getName()).append(argType);
                        break;
                    }
//                    else {
//                        operationString.append(", ").append("t").append(getTempVarCount()).append(".").append(arg.get("id"));
//                    }
                }
                if (!found) {
                    operationString.append(", ").append("t").append(getTempVarCount()).append(".").append(arg.get("id"));
                }
            }
        }

        if (invokeType.equals("invokespecial")) {
            operationString.append(")").append(".V");
        }
        else {
            operationString.append(")").append(returnType);
        }

        String opstring = operationString.toString();

        if (parent.isPresent()) {
            ollirCode.append(opstring);
        }
        else {
            ollirCode.append(opstring).append(";\n");
        }

        return "";
    }

    private String visitMethodCall(JmmNode methodCallNode, OllirInference inference) {

        //System.out.println("DEBUGGING METHODCALL NODE: " + methodCallNode);

        System.out.println("CHILD = " + methodCallNode.getChildren());

        String firstArg = "";
        String methodId = methodCallNode.get("method");  //get the method identifier, like println in io.println(a)
        //System.out.println("DEBUGGING METHOD ID: " + methodId);
        //System.out.println("DEBUGGING METHOD ID" + methodId);
        //String methodName = methodCallNode.get("method");  //get the method name, like println in io.println(a)
        //for loop to visit the rest of the children

        //list of args
        List<String> argsList = new ArrayList<>();

        for (var child : methodCallNode.getChildren()) {
            //argsList.add(methodCallNode.getJmmChild(i).get("id"));
            System.out.println("DEBUGGING CHILD: " + child);
            argsList.add(child.get("id"));
        }

        //System.out.println("DEBUGGING ARGS LIST: " + argsList);

        String invokeType = OllirUtils.getInvokeType(firstArg, st);
        String returnType = OllirUtils.getOllirType(st.getReturnType(getCurrentMethodName(methodCallNode))) + " ";

        //System.out.println("DEBUGGING INVOKE TYPE: " + invokeType);
        //System.out.println("DEBUGGING METHOD ID: " + methodId);

        List<Symbol> argsSymbols = new ArrayList<>();

        var localvars = st.getLocalVariables(getCurrentMethodName(methodCallNode));

        for (var localvar : localvars) {
            for (var arg : argsList) {
                if (localvar.getName().equals(arg)) {
                    argsSymbols.add(localvar);
                }
            }
        }

        System.out.println("DEBUGGING ARGS SYMBOLS: " + argsSymbols);

        var parent = methodCallNode.getAncestor("This").isPresent();

        StringBuilder operationString = new StringBuilder();

        if (parent) {
            operationString = new StringBuilder(invokeType + "(this, " + "\"" + methodId + "\"");
        }
        else {
            operationString = new StringBuilder(invokeType + "(" + "\"" + methodId + "\"");
        }

        //StringBuilder operationString = new StringBuilder(invokeType + "(" + "\"" + methodId + "\"");

        for (var arg : argsSymbols) {
            String argType = OllirUtils.getOllirType(arg.getType());
            operationString.append(", ").append(arg.getName()).append(argType);
        }

        operationString.append(")").append(returnType);

        System.out.println("DEBUGGING OPERATION STRING: " + operationString);

        if (parent) {
            return operationString.toString();
        }

        //convert strigbuilder to string

        String opstring = operationString.toString();

        ollirCode.append(getIndent()).append(opstring).append(";\n");

        return "";
    }

    private String visitThis(JmmNode thisNode, OllirInference inference) {
        System.out.println("VISITING THIS");

        for (JmmNode child : thisNode.getChildren()) {
            System.out.println("THIS CHILD = " + child);


            visit(child);
        }

        if (thisNode.getJmmChild(0).getKind().equals("Variable") && thisNode.getJmmChild(1).getKind().equals("Variable")) {
            var classField0 = st.getField(thisNode.getJmmChild(0).get("id"));
            var classField0Type = OllirUtils.getOllirType(classField0.getKey().getType());
            var classField1 = st.getField(thisNode.getJmmChild(1).get("id"));
            var classField1Type = OllirUtils.getOllirType(classField1.getKey().getType());
            ollirCode.append(getIndent()).append("putfield(this,").append(thisNode.getJmmChild(0).get("id")).append(classField0Type).append(",").append(thisNode.getJmmChild(1).get("id")).append(classField1Type).append(");\n");
        }
        else if (thisNode.getJmmChild(0).getKind().equals("Variable") && !thisNode.getJmmChild(1).getKind().equals("Variable")) {
            System.out.println("aaa");
            var classField0 = st.getField(thisNode.getJmmChild(0).get("id"));
            var classField0Type = OllirUtils.getOllirType(classField0.getKey().getType());
            var var2Type = "";
            if (thisNode.getJmmChild(1).getKind().equals("Integer")) {
                var2Type = ".i32";
            }
            else if (thisNode.getJmmChild(1).getKind().equals("Boolean")) {
                var2Type = ".bool";
            }
            ollirCode.append(getIndent()).append("putfield(this,").append(thisNode.getJmmChild(0).get("id")).append(classField0Type).append(",").append(thisNode.getJmmChild(1).get("value")).append(var2Type).append(").V;\n");
        }
        else if (thisNode.getJmmChild(0).getKind().equals("MethodCall")) {
            System.out.println("bbb");
            var methodCallNode = thisNode.getJmmChild(0);
            var returnType = OllirUtils.getOllirType(st.getReturnType(getCurrentMethodName(methodCallNode)));
            var methodCall = visit(thisNode.getJmmChild(0)); //visit the method call
            var newTemp = getAndAddTempVarCount(thisNode);
            ollirCode.append(getIndent()).append("t").append(newTemp).append(returnType).append(" :=").append(returnType).append(" ").append(methodCall).append(";\n");

            //ollirCode.append(getIndent()).append("invokevirtual(this,").append(thisNode.getJmmChild(0).get("method")).append(")").append(";\n");
            return "t" + newTemp + returnType; //para ir para o assignment
        }
        else {
            ollirCode.append("this.").append("putfield(this,").append(thisNode.getJmmChild(0).get("id")).append(");\n");;
        }

        //ollirCode.append("this");

        //for assignment
        return "";
    }

    private String visitNewObject(JmmNode newObjectNode, OllirInference inference) {
        System.out.println("VISITING NEW OBJECT");

        for (JmmNode child : newObjectNode.getChildren()) {
            visit(child);
        }

        var parent = newObjectNode.getAncestor("AccessMethod");


        String type = newObjectNode.get("id");

        var newTemp = "";

        if (parent.isPresent()) {
            newTemp = String.valueOf(getAndAddTempVarCount(newObjectNode));
            ollirCode.append("t").append(newTemp).append(".").append(type).append(" :=.").append(type).append(" new(").append(newObjectNode.get("id")).append(").").append(newObjectNode.get("id")).append(";\n");
            ollirCode.append(getIndent()).append("invokespecial(").append("t").append(newTemp).append(".").append(type).append(", \"<init>\").V").append(";\n");
        }
        else {
            System.out.println("ENTERED ELSE");
            String toAppend = "new(" + type + ")." + type;
            System.out.println("TO APPEND: " + toAppend);
            ollirCode.append("new(").append(type).append(").").append(type);
        }

        //ollirCode.append("new(").append(type).append(").").append(type);

        //for assignment
        //return "new " + newObjectNode.get("id");
        return "t" + newTemp + type;
    }
}
