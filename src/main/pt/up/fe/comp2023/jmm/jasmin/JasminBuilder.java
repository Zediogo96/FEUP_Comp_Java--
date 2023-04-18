package pt.up.fe.comp2023.jmm.jasmin;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;

import static org.specs.comp.ollir.InstructionType.BINARYOPER;
import static org.specs.comp.ollir.InstructionType.RETURN;


public class JasminBuilder implements JasminBackend {
    ClassUnit classUnit = null;
    int condNumber = 0;
    String superClass;

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        try {
            this.classUnit = ollirResult.getOllirClass();

            // SETUP classUnit
            this.classUnit.checkMethodLabels();
            this.classUnit.buildCFGs();
            this.classUnit.buildVarTables();

            System.out.println("Generating Jasmin code ...");

            String jasminCode = buildJasmin();
            List<Report> reports = new ArrayList<>();

            System.out.println("JASMIN CODE : \n" + jasminCode);

            return new JasminResult(ollirResult, jasminCode, reports);

        } catch (OllirErrorException e) {
            return new JasminResult(classUnit.getClassName(), null,
                    Collections.singletonList(Report.newError(Stage.GENERATION, -1, -1,
                            "Jasmin generation exception.", e)));
        }

    }

    private String buildJasmin() {
        StringBuilder jasminBuilder = new StringBuilder();

        jasminBuilder.append(".class ").append(this.classUnit.getClassName()).append("\n");

        this.superClass = this.classUnit.getSuperClass();
        if (this.superClass == null) {
            this.superClass = "java/lang/Object";
        }

        jasminBuilder.append(".super ").append(dealWithClassFullName(this.superClass)).append("\n");

        for (Field field : this.classUnit.getFields()) {
            StringBuilder access = new StringBuilder();
            if (field.getFieldAccessModifier() != AccessModifiers.DEFAULT) {
                access.append(field.getFieldAccessModifier().name().toLowerCase()).append(" ");
            }

            if (field.isStaticField()) {
                access.append("static ");
            }
            if (field.isInitialized()) {
                access.append("final ");
            }

            jasminBuilder.append(".field ").append(access).append(field.getFieldName())
                    .append(" ").append(this.dealWithFieldDescriptor(field.getFieldType())).append("\n");
        }

        for (Method method : this.classUnit.getMethods()) {
            jasminBuilder.append(this.dealWithMethodHeader(method));
            jasminBuilder.append(this.dealWithMethodStatements(method));
            jasminBuilder.append(".end method\n");
        }

        return jasminBuilder.toString();
    }

    private String dealWithMethodHeader(Method method) {
        StringBuilder jasminBuilder = new StringBuilder("\n.method ");

        // <access-spec>
        if (method.getMethodAccessModifier() != AccessModifiers.DEFAULT) {
            jasminBuilder.append(method.getMethodAccessModifier().name().toLowerCase()).append(" ");
        }

        if (method.isStaticMethod()) {
            jasminBuilder.append("static ");
        }
        if (method.isFinalMethod()) {
            jasminBuilder.append("final ");
        }

        if (method.isConstructMethod()) {
            jasminBuilder.append("public <init>");
        } else jasminBuilder.append(method.getMethodName());
        {
            jasminBuilder.append("(");
        }

        for (Element param : method.getParams()) {
            jasminBuilder.append(this.dealWithFieldDescriptor(param.getType()));
        }
        jasminBuilder.append(")");
        jasminBuilder.append(this.dealWithFieldDescriptor(method.getReturnType())).append("\n");

        return jasminBuilder.toString();
    }

    private String dealWithMethodStatements(Method method) {

        String methodInst = this.dealWithMethodInst(method);

        if (method.isConstructMethod()) {
            return methodInst;
        } else {
            return "\t.limit stack 99" + "\n" +
                    "\t.limit locals 99" + "\n" +
                    methodInst;
        }
    }

    private String dealWithMethodInst(Method method) {
        StringBuilder jasminBuilder = new StringBuilder();

        List<Instruction> methodInst = method.getInstructions();
        for (Instruction inst : methodInst) {
            for (Map.Entry<String, Instruction> label : method.getLabels().entrySet()) {
                if (label.getValue().equals(inst)) {
                    jasminBuilder.append(label.getKey()).append(":\n");
                }
            }
            jasminBuilder.append(this.dealWithInst(inst, method.getVarTable()));
            if (inst.getInstType() == InstructionType.CALL
                    && ((CallInstruction) inst).getReturnType().getTypeOfElement() != ElementType.VOID) {

                jasminBuilder.append("\tpop\n");
            }

        }

        boolean hasReturnInstruction = methodInst.size() > 0
                && methodInst.get(methodInst.size() - 1).getInstType() == RETURN;

        if (!hasReturnInstruction && method.getReturnType().getTypeOfElement() == ElementType.VOID) {
            jasminBuilder.append("\treturn\n");
        }

        return jasminBuilder.toString();
    }

    private String dealWithInst(Instruction inst, HashMap<String, Descriptor> varTable) {
        return switch (inst.getInstType()) {
            case ASSIGN -> this.dealWithAssign((AssignInstruction) inst, varTable);
            case CALL -> this.dealWithCall((CallInstruction) inst, varTable);
            case GOTO -> this.dealWithGoto((GotoInstruction) inst);
            case BRANCH -> this.dealWithBranch((CondBranchInstruction) inst, varTable);
            case RETURN -> this.dealWithReturn((ReturnInstruction) inst, varTable);
            case PUTFIELD -> this.dealWithPutField((PutFieldInstruction) inst, varTable);
            case GETFIELD -> this.dealWithGetField((GetFieldInstruction) inst, varTable);
            case UNARYOPER -> this.dealWithUnaryOper((UnaryOpInstruction) inst, varTable);
            case BINARYOPER -> this.dealWithBinaryOper((BinaryOpInstruction) inst, varTable);
            case NOPER -> this.dealWithLoadToStack(((SingleOpInstruction) inst).getSingleOperand(), varTable);
        };
    }

    private String dealWithUnaryOper(UnaryOpInstruction inst, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminBuilder = new StringBuilder();

        jasminBuilder.append(this.dealWithLoadToStack(inst.getOperand(), varTable))
                .append("\t").append(this.dealWithOper(inst.getOperation()));

        boolean isBooleanOperation = inst.getOperation().getOpType() == OperationType.NOTB;
        if (isBooleanOperation) {
            jasminBuilder.append(this.dealWithBoolOperResultToStack());
        } else {
            jasminBuilder.append("; Invalid UNARYOPER\n");
        }

        jasminBuilder.append("\n");
        return jasminBuilder.toString();
    }

    private String dealWithBinaryOper(BinaryOpInstruction inst, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminBuilder = new StringBuilder();

        Element leftElement = inst.getLeftOperand();
        Element rightElement = inst.getRightOperand();

        jasminBuilder.append(this.dealWithLoadToStack(leftElement, varTable))
                .append(this.dealWithLoadToStack(rightElement, varTable))
                .append("\t").append(this.dealWithOper(inst.getOperation()));

        OperationType operType = inst.getOperation().getOpType();
        boolean isBoolOper =
                operType == OperationType.EQ
                        || operType == OperationType.GTH
                        || operType == OperationType.GTE
                        || operType == OperationType.LTH
                        || operType == OperationType.LTE
                        || operType == OperationType.NEQ;

        if (isBoolOper) {
            jasminBuilder.append(this.dealWithBoolOperResultToStack());
        }

        jasminBuilder.append("\n");

        return jasminBuilder.toString();
    }

    private String dealWithBranch(CondBranchInstruction inst, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminBuilder = new StringBuilder();

        Instruction condition;
        if (inst instanceof SingleOpCondInstruction singleOperCond) {
            condition = singleOperCond.getCondition();

        } else if (inst instanceof OpCondInstruction operCond) {
            condition = operCond.getCondition();

        } else {
            return "; ERROR: invalid CondBranchInstruction instance\n";
        }

        String operation;
        switch (condition.getInstType()) {
            case BINARYOPER -> {
                assert condition instanceof BinaryOpInstruction;
                BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) condition;
                switch (binaryOpInstruction.getOperation().getOpType()) {
                    case LTH -> {
                        Element leftElement = binaryOpInstruction.getLeftOperand();
                        Element rightElement = binaryOpInstruction.getRightOperand();

                        Integer parsedInt = null;
                        Element otherElement = null;
                        operation = "if_icmplt";

                        // instruction selection for 0 < x
                        if (leftElement instanceof LiteralElement) {
                            String literal = ((LiteralElement) leftElement).getLiteral();
                            parsedInt = Integer.parseInt(literal);
                            otherElement = rightElement;
                            operation = "ifgt";

                            // instruction selection for x < 0
                        } else if (rightElement instanceof LiteralElement) {
                            String literal = ((LiteralElement) rightElement).getLiteral();
                            parsedInt = Integer.parseInt(literal);
                            otherElement = leftElement;
                            operation = "iflt";
                        }

                        if (parsedInt != null && parsedInt == 0) {
                            jasminBuilder.append(this.dealWithLoadToStack(otherElement, varTable));

                        } else {
                            jasminBuilder.append(this.dealWithLoadToStack(leftElement, varTable))
                                    .append(this.dealWithLoadToStack(rightElement, varTable));

                            operation = "if_icmplt";
                        }

                    }
                    case ANDB -> {
                        jasminBuilder.append(this.dealWithInst(condition, varTable));
                        operation = "ifne";
                    }
                    default -> {
                        // not supposed to happen
                        jasminBuilder.append("; Invalid BINARYOPER\n");
                        jasminBuilder.append(this.dealWithInst(condition, varTable));
                        operation = "ifne";
                    }
                }
            }
            case UNARYOPER -> {
                assert condition instanceof UnaryOpInstruction;
                UnaryOpInstruction unaryOper = (UnaryOpInstruction) condition;
                if (unaryOper.getOperation().getOpType() == OperationType.NOTB) {
                    jasminBuilder.append(this.dealWithLoadToStack(unaryOper.getOperand(), varTable));
                    operation = "ifeq";
                } else {
                    // not supposed to happen
                    jasminBuilder.append("; Invalid UNARYOPER\n");
                    jasminBuilder.append(this.dealWithInst(condition, varTable));
                    operation = "ifne";
                }
            }
            default -> {
                jasminBuilder.append(this.dealWithInst(condition, varTable));
                operation = "ifne";
            }
        }

        jasminBuilder.append("\t").append(operation).append(" ").append(inst.getLabel()).append("\n");

        return jasminBuilder.toString();
    }

    private String dealWithOper(Operation operation) {
        return switch (operation.getOpType()) {
            case LTH -> "if_icmplt";
            case ANDB -> "iand";
            case NOTB -> "ifeq";

            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";

            default -> "; ERROR: operation not implemented: " + operation.getOpType() + "\n";
        };
    }

    private String dealWithPutField(PutFieldInstruction inst, HashMap<String, Descriptor> varTable) {
        return this.dealWithLoadToStack(inst.getFirstOperand(), varTable) +
                this.dealWithLoadToStack(inst.getThirdOperand(), varTable) +
                "\tputfield " + this.dealWithClassFullName(((Operand) inst.getFirstOperand()).getName()) +
                "/" + ((Operand) inst.getSecondOperand()).getName() +
                " " + this.dealWithFieldDescriptor(inst.getSecondOperand().getType()) + "\n";
    }

    private String dealWithGetField(GetFieldInstruction inst, HashMap<String, Descriptor> varTable) {
        return this.dealWithLoadToStack(inst.getFirstOperand(), varTable) +
                "\tgetfield " + this.dealWithClassFullName(((Operand) inst.getFirstOperand()).getName()) +
                "/" + ((Operand) inst.getSecondOperand()).getName() +
                " " + this.dealWithFieldDescriptor(inst.getSecondOperand().getType()) + "\n";
    }

    private String dealWithReturn(ReturnInstruction inst, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminBuilder = new StringBuilder();

        if (inst.hasReturnValue()) {
            jasminBuilder.append(this.dealWithLoadToStack(inst.getOperand(), varTable));
        }

        jasminBuilder.append("\t");
        if (inst.getOperand() != null) {
            ElementType elementType = inst.getOperand().getType().getTypeOfElement();

            if (elementType == ElementType.INT32 || elementType == ElementType.BOOLEAN) {
                jasminBuilder.append("i");
            } else {
                jasminBuilder.append("a");
            }
        }

        jasminBuilder.append("return\n");

        return jasminBuilder.toString();
    }

    private String dealWithGoto(GotoInstruction inst) {
        return "\tgoto " + inst.getLabel() + "\n";
    }

    private String dealWithLoadToStack(Element element, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminBuilder = new StringBuilder();

        if (element instanceof Operand && !varTable.containsKey(((Operand) element).getName())) {
            Operand operand = (Operand) element;
            if (operand.getName().equals("false")) {
                jasminBuilder.append("\ticonst_0");
                jasminBuilder.append("\n");
                return jasminBuilder.toString();
            } else if (operand.getName().equals("true")) {
                jasminBuilder.append("\ticonst_1");
                jasminBuilder.append("\n");
                return jasminBuilder.toString();
            }
        }
        if (element instanceof LiteralElement) {
            String literal = ((LiteralElement) element).getLiteral();

            if (element.getType().getTypeOfElement() == ElementType.INT32
                    || element.getType().getTypeOfElement() == ElementType.BOOLEAN) {

                int parsedInt = Integer.parseInt(literal);

                if (parsedInt >= -1 && parsedInt <= 5) { // [-1,5]
                    jasminBuilder.append("\ticonst_");
                } else if (parsedInt >= -128 && parsedInt <= 127) { // byte
                    jasminBuilder.append("\tbipush ");
                } else if (parsedInt >= -32768 && parsedInt <= 32767) { // short
                    jasminBuilder.append("\tsipush ");
                } else {
                    jasminBuilder.append("\tldc "); // int
                }

                if (parsedInt == -1) {
                    jasminBuilder.append("m1");
                } else {
                    jasminBuilder.append(parsedInt);
                }
            } else {
                jasminBuilder.append("\tldc ").append(literal);
            }
        } else if (element instanceof ArrayOperand) {
            ArrayOperand operand = (ArrayOperand) element;
            jasminBuilder.append("\taload").append(this.dealWithVariableNumber(operand.getName(), varTable)).append("\n"); // load array (ref)
            jasminBuilder.append(dealWithLoadToStack(operand.getIndexOperands().get(0), varTable)); // load index
            jasminBuilder.append("\tiaload");
        } else if (element instanceof Operand) {
            Operand operand = (Operand) element;
            switch (operand.getType().getTypeOfElement()) {
                case INT32, BOOLEAN ->
                        jasminBuilder.append("\tiload").append(this.dealWithVariableNumber(operand.getName(), varTable));
                case OBJECTREF, STRING, ARRAYREF ->
                        jasminBuilder.append("\taload").append(this.dealWithVariableNumber(operand.getName(), varTable));
                case THIS -> jasminBuilder.append("\taload_0");
                default ->
                        jasminBuilder.append("; ERROR: getLoadToStack() operand ").append(operand.getType().getTypeOfElement()).append("\n");
            }
        } else {
            jasminBuilder.append("; ERROR: getLoadToStack() invalid element instance\n");
        }
        jasminBuilder.append("\n");
        return jasminBuilder.toString();
    }

    private String dealWithCall(CallInstruction inst, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminBuilder = new StringBuilder();

        switch (inst.getInvocationType()) {
            case invokevirtual -> {
                jasminBuilder.append(this.dealWithLoadToStack(inst.getFirstArg(), varTable));

                for (Element element : inst.getListOfOperands()) {
                    jasminBuilder.append(this.dealWithLoadToStack(element, varTable));
                }

                jasminBuilder.append("\tinvokevirtual ")
                        .append(this.dealWithClassFullName(((ClassType) inst.getFirstArg().getType()).getName()))
                        .append("/").append(((LiteralElement) inst.getSecondArg()).getLiteral().replace("\"", ""))
                        .append("(");

                for (Element element : inst.getListOfOperands()) {
                    jasminBuilder.append(this.dealWithFieldDescriptor(element.getType()));
                }

                jasminBuilder.append(")").append(this.dealWithFieldDescriptor(inst.getReturnType())).append("\n");


            }
            case invokespecial -> {
                jasminBuilder.append(this.dealWithLoadToStack(inst.getFirstArg(), varTable));


                jasminBuilder.append("\tinvokespecial ");

                if (inst.getFirstArg().getType().getTypeOfElement() == ElementType.THIS) {
                    jasminBuilder.append(this.superClass);
                } else {
                    String className = this.dealWithClassFullName(((ClassType) inst.getFirstArg().getType()).getName());
                    jasminBuilder.append(className);
                }

                jasminBuilder.append("/").append("<init>(");

                for (Element element : inst.getListOfOperands()) {
                    jasminBuilder.append(this.dealWithFieldDescriptor(element.getType()));
                }

                jasminBuilder.append(")").append(this.dealWithFieldDescriptor(inst.getReturnType())).append("\n");
            }
            case invokestatic -> {

                for (Element element : inst.getListOfOperands()) {
                    jasminBuilder.append(this.dealWithLoadToStack(element, varTable));
                }

                jasminBuilder.append("\tinvokestatic ")
                        .append(this.dealWithClassFullName(((Operand) inst.getFirstArg()).getName()))
                        .append("/").append(((LiteralElement) inst.getSecondArg()).getLiteral().replace("\"", ""))
                        .append("(");

                for (Element element : inst.getListOfOperands()) {
                    jasminBuilder.append(this.dealWithFieldDescriptor(element.getType()));
                }

                jasminBuilder.append(")").append(this.dealWithFieldDescriptor(inst.getReturnType())).append("\n");
            }
            case NEW -> {
                ElementType elementType = inst.getReturnType().getTypeOfElement();

                if (elementType == ElementType.OBJECTREF) {
                    for (Element element : inst.getListOfOperands()) {
                        jasminBuilder.append(this.dealWithLoadToStack(element, varTable));
                    }
                    jasminBuilder.append("\tnew ").append(this.dealWithClassFullName(((Operand) inst.getFirstArg()).getName())).append("\n");
                } else if (elementType == ElementType.ARRAYREF) {
                    for (Element element : inst.getListOfOperands()) {
                        jasminBuilder.append(this.dealWithLoadToStack(element, varTable));
                    }

                    jasminBuilder.append("\tnewarray ");

                    if (inst.getListOfOperands().get(0).getType().getTypeOfElement() == ElementType.INT32) {
                        jasminBuilder.append("int\n");
                    } else {
                        jasminBuilder.append("; only int arrays are implemented\n");
                    }
                } else {
                    jasminBuilder.append("; ERROR: NEW invocation type not implemented\n");
                }
            }
            case arraylength -> {
                jasminBuilder.append(this.dealWithLoadToStack(inst.getFirstArg(), varTable));
                jasminBuilder.append("\tarraylength\n");
            }
            case ldc -> jasminBuilder.append(this.dealWithLoadToStack(inst.getFirstArg(), varTable));
            default -> jasminBuilder.append("; ERROR: call instruction not implemented\n");
        }

        return jasminBuilder.toString();
    }

    private String dealWithAssign(AssignInstruction inst, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminBuilder = new StringBuilder();

        Operand dest = (Operand) inst.getDest();
        if (dest instanceof ArrayOperand arrayOperand) {
            jasminBuilder.append("\taload").append(this.dealWithVariableNumber(arrayOperand.getName(), varTable)).append("\n"); // load array (ref)
            jasminBuilder.append(this.dealWithLoadToStack(arrayOperand.getIndexOperands().get(0), varTable)); // load index

        } else {
            if (inst.getRhs().getInstType() == BINARYOPER) {
                BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) inst.getRhs();

                if (binaryOpInstruction.getOperation().getOpType() == OperationType.ADD) {
                    boolean leftIsLiteral = binaryOpInstruction.getLeftOperand().isLiteral();
                    boolean rightIsLiteral = binaryOpInstruction.getRightOperand().isLiteral();

                    LiteralElement literal = null;
                    Operand operand = null;

                    if (leftIsLiteral && !rightIsLiteral) {
                        literal = (LiteralElement) binaryOpInstruction.getLeftOperand();
                        operand = (Operand) binaryOpInstruction.getRightOperand();
                    } else if (!leftIsLiteral && rightIsLiteral) {
                        literal = (LiteralElement) binaryOpInstruction.getRightOperand();
                        operand = (Operand) binaryOpInstruction.getLeftOperand();
                    }

                    if (literal != null && operand != null) {
                        if (operand.getName().equals(dest.getName())) {
                            int literalValue = Integer.parseInt((literal).getLiteral());

                            if (literalValue >= -128 && literalValue <= 127) {
                                return "\tiinc " + varTable.get(operand.getName()).getVirtualReg() + " " + literalValue + "\n";
                            }
                        }
                    }
                }
            }
        }

        jasminBuilder.append(this.dealWithInst(inst.getRhs(), varTable));
        jasminBuilder.append(this.dealWithStore(dest, varTable)); // store in array[index] if (dest instanceof ArrayOperand)

        return jasminBuilder.toString();
    }

    private String dealWithStore(Operand dest, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminBuilder = new StringBuilder();

        switch (dest.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                if (varTable.get(dest.getName()).getVarType().getTypeOfElement() == ElementType.ARRAYREF) {
                    jasminBuilder.append("\tiastore").append("\n");
                } else {
                    jasminBuilder.append("\tistore").append(this.dealWithVariableNumber(dest.getName(), varTable)).append("\n");

                }
            }
            case OBJECTREF, THIS, STRING, ARRAYREF -> {
                jasminBuilder.append("\tastore").append(this.dealWithVariableNumber(dest.getName(), varTable)).append("\n");
            }
            default -> jasminBuilder.append("; ERROR: getStore()\n");
        }
        return jasminBuilder.toString();
    }

    private String dealWithVariableNumber(String name, HashMap<String, Descriptor> varTable) {
        if (name.equals("this")) {
            return "_0";
        }
        int virtualRegister = varTable.get(name).getVirtualReg();

        StringBuilder stringBuilder = new StringBuilder();

        if (virtualRegister < 4) stringBuilder.append("_");
        else stringBuilder.append(" ");

        stringBuilder.append(virtualRegister);

        return stringBuilder.toString();
    }

    private String dealWithFieldDescriptor(Type type) {
        StringBuilder jasminBuilder = new StringBuilder();
        ElementType elementType = type.getTypeOfElement();

        if (elementType == ElementType.ARRAYREF) {
            jasminBuilder.append("[");
            //noinspection deprecation
            elementType = ((ArrayType) type).getArrayType();
        }

        switch (elementType) {
            case INT32 -> jasminBuilder.append("I");
            case BOOLEAN -> jasminBuilder.append("Z");
            case OBJECTREF -> {
                assert type instanceof ClassType;
                String name = ((ClassType) type).getName();
                jasminBuilder.append("L").append(this.dealWithClassFullName(name)).append(";");
            }
            case STRING -> jasminBuilder.append("Ljava/lang/String;");
            case VOID -> jasminBuilder.append("V");
            default -> jasminBuilder.append("; ERROR: descriptor type not implemented\n");
        }

        return jasminBuilder.toString();
    }

    private String dealWithClassFullName(String classNameWithoutImports) {
        if (classNameWithoutImports.equals("this")) {
            return this.classUnit.getClassName();
        }

        for (String importName : this.classUnit.getImports()) {
            if (importName.endsWith(classNameWithoutImports)) {
                return importName.replaceAll("\\.", "/");
            }
        }

        return classNameWithoutImports;
    }

    private String dealWithBoolOperResultToStack() {
        return " TRUE" + this.condNumber + "\n"
                + "\ticonst_0\n"
                + "\tgoto NEXT" + this.condNumber + "\n"
                + "TRUE" + this.condNumber + ":\n"
                + "\ticonst_1\n"
                + "NEXT" + this.condNumber++ + ":";
    }

}