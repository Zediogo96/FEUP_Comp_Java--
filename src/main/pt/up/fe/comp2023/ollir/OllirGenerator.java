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
        addVisit("BinaryOp", this::visitBinaryOperator);
        addVisit("This", this::visitThis);
        addVisit("AccessMethod", this::visitAccessMethod);
        addVisit("MethodCall", this::visitMethodCall);

        setDefaultVisit((node, dummy) -> null);
    }

    //TODO: visit assignments and arithmetic operations (with correct precedence)

    public OllirGenerator(MySymbolTable st, boolean optimize) {
        this.ollirCode = new StringBuilder();
        this.st = st;
        this.optimize = optimize;
        this.indent = 0;
        this.tempVarCount = 0;
        this.ifThenElseCount = 1;
        this.whileCount = 1;

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


    private int getAndAddIfThenElseCount() {
        return this.ifThenElseCount++;
    }

    private int getAndAddWhileCount() {
        return this.whileCount++;
    }

    private int getAndAddTempVarCount(JmmNode node) {
        //TODO: check if this is correct

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
        System.out.println("IMPORTS:" + st.getImports());

        List<String> sublists = new ArrayList<String>();

        for (var importStr : st.getImports()) {
            System.out.println("IMPORTS SUBLIST:" + importStr);
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
        if (superClassName != null) {
            ollirCode.append(" extends ").append(superClassName);
        }
        ollirCode.append(" {\n");
        this.addIndent();

        //fields

        for (var field : st.getFields()) {
            ollirCode.append(getIndent()).append(".field ").append(field.getName()).append(OllirUtils.getOllirType(field.getType())).append(";\n");
        }

        //default constructor

        ollirCode.append(getIndent()).append(".construct").append(st.getClassName()).append("().V {\n");
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
            if (counter == 6) {var type = child.getKind(); System.out.println("Type of child is: " + type); System.out.println("Children of child are: " + child.getChildren());}
            visit(child);
//            if (counter == 6) {
//                var childofStmt = child.getJmmChild(0);
//                //System.out.println("Child of statement is: " + childofStmt);
//                var methodCallChildren = childofStmt.getChildren();
//                //System.out.println("Children of method call are: " + methodCallChildren);
//            }

        }

        this.removeIndent();

        ollirCode.append(getIndent()).append("}\n");

        return "";
    }

    private String visitAssignment(JmmNode assignmentNode, OllirInference inference) {

        System.out.println("VISITING ASSIGNMENT NODE: " + assignmentNode);
        System.out.println("VISITING ASSIGNMENT NODE CHILDREN: " + assignmentNode.getChildren());

        String toAssign = assignmentNode.get("id");
        var toAssignSymbol = st.getLocalVariableFromMethod(getCurrentMethodName(assignmentNode), toAssign);
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
            var localvars = st.getLocalVariables(getCurrentMethodName(assignmentNode));

            Symbol varSymbol = null;
            for (var localvar : localvars) {
                if (localvar.getName().equals(varid)) {
                    varSymbol = localvar;
                }
            }
            //System.out.println("VAR SYMBOL: " + varSymbol);
            type = OllirUtils.getOllirType(varSymbol.getType());
        }

        ollirCode.append(getIndent()).append(toAssign).append(toAssignType).append(" :=").append(type).append(" ");  //.append(str_assigned).append(";\n");  //append generally the assignment  structure, then each visitor will append the rhs

        String str_assigned;

        for (JmmNode child : assignmentNode.getChildren()) {
            String returnstr = visit(child);
            ollirCode.append(returnstr);
        }

        ollirCode.append(";\n");

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
            System.out.println("PARAMS: " + _params);
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
                System.out.println("PARAMETERS VAR: " + parameters_var);
                var varTypeInt = parameters_var.getKey().getType();
                varType = OllirUtils.getOllirType(varTypeInt);
            } else {
                var varTypeInt = variable.getType();
                varType = OllirUtils.getOllirType(varTypeInt);
            }

        }

        //ollirCode.append(varName).append(varType);

        String str = varName + varType;

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

        //System.out.println("DEBUGGING RETURN NODE: " + returnNode);
        JmmNode exprNode = returnNode.getJmmChild(0);
        if (exprNode == null) {
            //return "null"; // or whatever default value you want to use
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


    //BINARY OPERATORS











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

    //reorder this

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

    private String NegationVisit(JmmNode negationNode, OllirInference inference) {
        String toNegate = visit(negationNode.getJmmChild(0), new OllirInference(".bool", true));
        String opStr = "!.bool " + toNegate;
        if (inference == null || inference.getIsAssignedToTempVar()) {
            int tempVar = getAndAddTempVarCount(negationNode);  //TODO: correct this
            ollirCode.append(getIndent()).append("t").append(tempVar).append(".bool").append(" :=.bool ").append(opStr).append(";\n");
            return "t" + tempVar;
        } else {
            return opStr;
        }
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

        String op = binaryOperator.get("op");
        String opstring = OllirUtils.getOperator(op);

        String left = visit(binaryOperator.getJmmChild(0));
        String right = visit(binaryOperator.getJmmChild(1));;

        String result = left + " " + opstring + " " + right;


        return result;
    }




    private String visitAccessMethod(JmmNode methodCallNode, OllirInference inference) {

        System.out.println("DEBUGGING METHODCALL NODE: " + methodCallNode);

        System.out.println("CHILD = " + methodCallNode.getChildren());

        //passar esta parte para expression

        // if parent node is of this type TODO: This part is for THIS

//        var ancestor = methodCallNode.getAncestor("This");
//        System.out.println("DEBUGGING ANCESTOR: " + ancestor);
//
//        if (methodCallNode.getAncestor("This").isPresent()) {
//            System.out.println(methodCallNode);
//        }



//        else if (methodCallNode.getAncestor("MethodCall").isPresent()) {
//            //visit methodcall
//            System.out.println("DEBUGGING METHODCALL");
//        }

//        var firstChildKind = methodCallNode.getJmmChild(0).getKind();
//
//        if (firstChildKind.equals("This")) {
//            //visit this
//            System.out.println("DEBUGGING THIS");
//        }
//        else if (firstChildKind.equals("MethodCall")) {
//            //visit methodcall
//            System.out.println("DEBUGGING METHODCALL");
//        }

        //method call like io.println(a)

        String firstArg = methodCallNode.getJmmChild(0).get("id");  //get the first identifier of the method call, like io in io.println(a)
        System.out.println("DEBUGGING FIRST ARG: " + firstArg);
        String methodId = methodCallNode.getJmmChild(1).get("id");  //get the method identifier, like println in io.println(a)
        System.out.println("DEBUGGING METHOD ID: " + methodId);
        System.out.println("DEBUGGING METHOD ID" + methodId);
        //String methodName = methodCallNode.get("method");  //get the method name, like println in io.println(a)
        //for loop to visit the rest of the children

        //list of args
        List<String> argsList = new ArrayList<>();

        for (int i = 2; i < methodCallNode.getChildren().size(); i++) {
            argsList.add(methodCallNode.getJmmChild(i).get("id"));
        }

        System.out.println("DEBUGGING ARGS LIST: " + argsList);

        String invokeType = OllirUtils.getInvokeType(firstArg, st);
        String returnType = OllirUtils.getOllirType(st.getReturnType(getCurrentMethodName(methodCallNode))) + " ";

        System.out.println("DEBUGGING INVOKE TYPE: " + invokeType);
        System.out.println("DEBUGGING METHOD ID: " + methodId);

        //parse the children of the method call to get the arguments, but ignore the first one, which is the identifier

//        String args = "";
//        List<String> argsList = new ArrayList<>();
//        for (int i = 1; i < methodCallNode.getChildren().size(); i++) {
//            args += visit(methodCallNode.getJmmChild(i));
//            argsList.add(visit(methodCallNode.getJmmChild(i)));
//            if (i != methodCallNode.getChildren().size() - 1) {
//                args += ", ";
//            }
//        }

        //System.out.println("DEBUGGING ARGS: " + args);
        //System.out.println("DEBUGGING ARGS LIST: " + argsList);

        List<Symbol> argsSymbols = new ArrayList<>();

        var localvars = st.getLocalVariables(getCurrentMethodName(methodCallNode));

        //System.out.println("DEBUGGING LOCAL VARS: " + localvars);

        for (var localvar : localvars) {
            for (var arg : argsList) {
                if (localvar.getName().equals(arg)) {
                    argsSymbols.add(localvar);
                }
            }
        }

        System.out.println("DEBUGGING ARGS SYMBOLS: " + argsSymbols);

        StringBuilder operationString = new StringBuilder(invokeType + "(" + firstArg + ", \"" + methodId + "\"");

        for (var arg : argsSymbols) {
            String argType = OllirUtils.getOllirType(arg.getType());
            operationString.append(", ").append(arg.getName()).append(argType);
        }

        operationString.append(")").append(returnType);

        System.out.println("DEBUGGING OPERATION STRING: " + operationString);

        //convert strigbuilder to string

        String opstring = operationString.toString();

        ollirCode.append(getIndent()).append(opstring).append(";\n");

        return "";
    }





    private String visitMethodCall(JmmNode methodCallNode, OllirInference inference) {

        System.out.println("DEBUGGING METHODCALL NODE: " + methodCallNode);

        System.out.println("CHILD = " + methodCallNode.getChildren());

        String firstArg = "";
        String methodId = methodCallNode.get("method");  //get the method identifier, like println in io.println(a)
        System.out.println("DEBUGGING METHOD ID: " + methodId);
        System.out.println("DEBUGGING METHOD ID" + methodId);
        //String methodName = methodCallNode.get("method");  //get the method name, like println in io.println(a)
        //for loop to visit the rest of the children

        //list of args
        List<String> argsList = new ArrayList<>();

        for (var child : methodCallNode.getChildren()) {
            //argsList.add(methodCallNode.getJmmChild(i).get("id"));
            System.out.println("DEBUGGING CHILD: " + child);
            argsList.add(child.get("id"));
        }

        System.out.println("DEBUGGING ARGS LIST: " + argsList);

        String invokeType = OllirUtils.getInvokeType(firstArg, st);
        String returnType = OllirUtils.getOllirType(st.getReturnType(getCurrentMethodName(methodCallNode))) + " ";

        System.out.println("DEBUGGING INVOKE TYPE: " + invokeType);
        System.out.println("DEBUGGING METHOD ID: " + methodId);

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

        StringBuilder operationString = new StringBuilder(invokeType + "(" + "\"" + methodId + "\"");

        for (var arg : argsSymbols) {
            String argType = OllirUtils.getOllirType(arg.getType());
            operationString.append(", ").append(arg.getName()).append(argType);
        }

        operationString.append(")").append(returnType);

        System.out.println("DEBUGGING OPERATION STRING: " + operationString);

        //convert strigbuilder to string

        String opstring = operationString.toString();

        ollirCode.append(getIndent()).append(opstring).append(";\n");

        return "";
    }











    private String visitThis(JmmNode thisNode, OllirInference inference) {
        System.out.println("VISITING THIS");

        for (JmmNode child : thisNode.getChildren()) {
            visit(child);
        }

        //for assignment
        return "this";
    }
}
