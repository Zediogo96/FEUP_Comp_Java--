package pt.up.fe.comp2023.jasmin;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import java.util.*;
import static org.specs.comp.ollir.InstructionType.RETURN;
import static org.specs.comp.ollir.InstructionType.BINARYOPER;

@SuppressWarnings({"SpellCheckingInspection"})
public class JasminBuilder implements JasminBackend {
    ClassUnit classUnit = null;
    int conditionalNumber = 0;
    int stackLimit = 0;
    String superClass;

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        try {
            this.classUnit = ollirResult.getOllirClass();
            this.classUnit.checkMethodLabels();
            this.classUnit.buildCFGs();
            this.classUnit.buildVarTables();

            String jasminCode = buildJasminCode();
            List<Report> reports = new ArrayList<>();

            if (ollirResult.getConfig().get("debug") != null && ollirResult.getConfig().get("debug").equals("true")) {
                System.out.println("JASMIN CODE : \n" + jasminCode);
            }

            return new JasminResult(ollirResult, jasminCode, reports);

        } catch (OllirErrorException e) {
            return new JasminResult(classUnit.getClassName(), null,
                    Collections.singletonList(Report.newError(Stage.GENERATION, -1, -1,
                            "Jasmin generation exception.", e)));
        }

    }

    private String buildJasminCode() {
        StringBuilder jasminString = new StringBuilder();

        // Class
        jasminString.append(".class ").append(this.classUnit.getClassName()).append("\n");

        this.superClass = this.classUnit.getSuperClass();
        if (this.superClass == null) {
            this.superClass = "java/lang/Object";
        }

        jasminString.append(".super ").append(dealWithClassFullName(this.superClass)).append("\n");

        // Fields
        for (Field field : this.classUnit.getFields()) {
            // .field <access-spec> <field-name> <descriptor>
            StringBuilder accessSpec = new StringBuilder();
            if (field.getFieldAccessModifier() != AccessModifiers.DEFAULT) {
                accessSpec.append(field.getFieldAccessModifier().name().toLowerCase()).append(" ");
            }

            if (field.isStaticField()) {
                accessSpec.append("static ");
            }
            if (field.isInitialized()) {
                accessSpec.append("final ");
            }

            jasminString.append(".field ").append(accessSpec).append(field.getFieldName())
                    .append(" ").append(this.dealWithFieldDesc(field.getFieldType())).append("\n");
        }

        // Methods
        for (Method method : this.classUnit.getMethods()) {
            // .method <access-spec> <method-spec>
            //     <statements>
            // .end method
            jasminString.append(this.dealWithMethodHeader(method));
            jasminString.append(this.dealWithMethodStatements(method));
            jasminString.append(".end method\n");
        }

        return jasminString.toString();
    }

    private String dealWithMethodHeader(Method method) {
        StringBuilder jasminString = new StringBuilder("\n.method ");

        // <access-spec>
        if (method.getMethodAccessModifier() != AccessModifiers.DEFAULT) {
            jasminString.append(method.getMethodAccessModifier().name().toLowerCase()).append(" ");
        }

        if (method.isStaticMethod()) jasminString.append("static ");
        if (method.isFinalMethod()) jasminString.append("final ");

        // <method-spec>
        if (method.isConstructMethod()) jasminString.append("<init>");
        else jasminString.append(method.getMethodName());
        jasminString.append("(");

        for (Element param : method.getParams()) {
            jasminString.append(this.dealWithFieldDesc(param.getType()));
        }
        jasminString.append(")");
        jasminString.append(this.dealWithFieldDesc(method.getReturnType())).append("\n");

        return jasminString.toString();
    }

    private String dealWithMethodStatements(Method method) {
        this.stackLimit = 99;
        String methodInstructions = this.dealWithMethodInstructions(method);

        return "\t.limit stack " + this.stackLimit + "\n" +
                "\t.limit locals 99\n" +
                methodInstructions;
    }

    private String dealWithMethodInstructions(Method method) {
        StringBuilder jasminString = new StringBuilder();

        List<Instruction> methodInstructions = method.getInstructions();
        for (Instruction instruction : methodInstructions) {
            // LABEL:
            for (Map.Entry<String, Instruction> label : method.getLabels().entrySet()) {
                if (label.getValue().equals(instruction)) {
                    jasminString.append(label.getKey()).append(":\n");
                }
            }

            jasminString.append(this.dealWithInstructions(instruction, method.getVarTable()));
            if (instruction.getInstType() == InstructionType.CALL
                    && ((CallInstruction) instruction).getReturnType().getTypeOfElement() != ElementType.VOID) {

                jasminString.append("\tpop\n");
            }

        }

        boolean hasReturnInstruction = methodInstructions.size() > 0
                && methodInstructions.get(methodInstructions.size() - 1).getInstType() == RETURN;

        if (!hasReturnInstruction && method.getReturnType().getTypeOfElement() == ElementType.VOID) {
            jasminString.append("\treturn\n");
        }

        return jasminString.toString();
    }

    private String dealWithInstructions(Instruction instruction, HashMap<String, Descriptor> varTable) {
        return switch (instruction.getInstType()) {
            case ASSIGN -> this.dealWithAssignInstruction((AssignInstruction) instruction, varTable);
            case CALL -> this.dealWithCallInstruction((CallInstruction) instruction, varTable);
            case GOTO -> this.dealWithGotoInstruction((GotoInstruction) instruction);
            case BRANCH -> this.dealWithBranchInstruction((CondBranchInstruction) instruction, varTable);
            case RETURN -> this.dealWithReturnInstruction((ReturnInstruction) instruction, varTable);
            case PUTFIELD -> this.dealWithPutFieldInstruction((PutFieldInstruction) instruction, varTable);
            case GETFIELD -> this.dealWithGetFieldInstruction((GetFieldInstruction) instruction, varTable);
            case UNARYOPER -> this.dealWithUnaryOpInstruction((UnaryOpInstruction) instruction, varTable);
            case BINARYOPER -> this.dealWithBinaryOpInstruction((BinaryOpInstruction) instruction, varTable);
            case NOPER -> this.dealWithLoadToStack(((SingleOpInstruction) instruction).getSingleOperand(), varTable);
        };
    }

    private String dealWithUnaryOpInstruction(UnaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminString = new StringBuilder();

        jasminString.append(this.dealWithLoadToStack(instruction.getOperand(), varTable))
                .append("\t").append(this.dealWithOp(instruction.getOperation()));

        boolean isBooleanOperation = instruction.getOperation().getOpType() == OperationType.NOTB;
        if (isBooleanOperation) {
            jasminString.append(this.dealWithBoolOpResultStack());
        } else {
            jasminString.append("; Invalid UNARYOPER\n");
        }

        jasminString.append("\n");
        return jasminString.toString();
    }

    private String dealWithBinaryOpInstruction(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminString = new StringBuilder();

        Element leftElement = instruction.getLeftOperand();
        Element rightElement = instruction.getRightOperand();

        jasminString.append(this.dealWithLoadToStack(leftElement, varTable))
                .append(this.dealWithLoadToStack(rightElement, varTable))
                .append("\t").append(this.dealWithOp(instruction.getOperation()));

        OperationType opType = instruction.getOperation().getOpType();
        boolean isBooleanOperation =
                opType == OperationType.EQ
                        || opType == OperationType.GTH
                        || opType == OperationType.GTE
                        || opType == OperationType.LTH
                        || opType == OperationType.LTE
                        || opType == OperationType.NEQ;

        if (isBooleanOperation) {
            jasminString.append(this.dealWithBoolOpResultStack());
        }

        jasminString.append("\n");

        return jasminString.toString();
    }

    private String dealWithBranchInstruction(CondBranchInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminString = new StringBuilder();

        Instruction condition;
        if (instruction instanceof SingleOpCondInstruction singleOpCondInstruction) {
            condition = singleOpCondInstruction.getCondition();

        } else if (instruction instanceof OpCondInstruction opCondInstruction) {
            condition = opCondInstruction.getCondition();

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
                            jasminString.append(this.dealWithLoadToStack(otherElement, varTable));

                        } else {
                            jasminString.append(this.dealWithLoadToStack(leftElement, varTable))
                                    .append(this.dealWithLoadToStack(rightElement, varTable));

                            operation = "if_icmplt";
                        }

                    }
                    case ANDB -> {
                        jasminString.append(this.dealWithInstructions(condition, varTable));
                        operation = "ifne";
                    }
                    default -> {
                        // not supposed to happen
                        jasminString.append("; Invalid BINARYOPER\n");
                        jasminString.append(this.dealWithInstructions(condition, varTable));
                        operation = "ifne";
                    }
                }
            }
            case UNARYOPER -> {
                assert condition instanceof UnaryOpInstruction;
                UnaryOpInstruction unaryOpInstruction = (UnaryOpInstruction) condition;
                if (unaryOpInstruction.getOperation().getOpType() == OperationType.NOTB) {
                    jasminString.append(this.dealWithLoadToStack(unaryOpInstruction.getOperand(), varTable));
                    operation = "ifeq";
                } else {
                    // not supposed to happen
                    jasminString.append("; Invalid UNARYOPER\n");
                    jasminString.append(this.dealWithInstructions(condition, varTable));
                    operation = "ifne";
                }
            }
            default -> {
                jasminString.append(this.dealWithInstructions(condition, varTable));
                operation = "ifne";
            }
        }

        jasminString.append("\t").append(operation).append(" ").append(instruction.getLabel()).append("\n");


        return jasminString.toString();
    }

    private String dealWithOp(Operation operation) {
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

    private String dealWithPutFieldInstruction(PutFieldInstruction instruction, HashMap<String, Descriptor> varTable) {

        return this.dealWithLoadToStack(instruction.getFirstOperand(), varTable) +
                this.dealWithLoadToStack(instruction.getThirdOperand(), varTable) +
                "\tputfield " + this.dealWithClassFullName(((Operand) instruction.getFirstOperand()).getName()) +
                "/" + ((Operand) instruction.getSecondOperand()).getName() +
                " " + this.dealWithFieldDesc(instruction.getSecondOperand().getType()) + "\n";
    }

    private String dealWithGetFieldInstruction(GetFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        return this.dealWithLoadToStack(instruction.getFirstOperand(), varTable) +
                "\tgetfield " + this.dealWithClassFullName(((Operand) instruction.getFirstOperand()).getName()) +
                "/" + ((Operand) instruction.getSecondOperand()).getName() +
                " " + this.dealWithFieldDesc(instruction.getSecondOperand().getType()) + "\n";
    }

    private String dealWithReturnInstruction(ReturnInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminString = new StringBuilder();

        if (instruction.hasReturnValue()) {
            jasminString.append(this.dealWithLoadToStack(instruction.getOperand(), varTable));
        }

        jasminString.append("\t");
        if (instruction.getOperand() != null) {
            ElementType elementType = instruction.getOperand().getType().getTypeOfElement();

            if (elementType == ElementType.INT32 || elementType == ElementType.BOOLEAN) {
                jasminString.append("i");
            } else {
                jasminString.append("a");
            }
        }

        jasminString.append("return\n");

        return jasminString.toString();
    }

    private String dealWithGotoInstruction(GotoInstruction instruction) {
        return "\tgoto " + instruction.getLabel() + "\n";
    }

    private String dealWithLoadToStack(Element element, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminString = new StringBuilder();

        if (element instanceof LiteralElement) {
            String literal = ((LiteralElement) element).getLiteral();

            if (element.getType().getTypeOfElement() == ElementType.INT32
                    || element.getType().getTypeOfElement() == ElementType.BOOLEAN) {

                int parsedInt = Integer.parseInt(literal);

                if (parsedInt >= -1 && parsedInt <= 5) { // [-1,5]
                    jasminString.append("\ticonst_");
                } else if (parsedInt >= -128 && parsedInt <= 127) { // byte
                    jasminString.append("\tbipush ");
                } else if (parsedInt >= -32768 && parsedInt <= 32767) { // short
                    jasminString.append("\tsipush ");
                } else {
                    jasminString.append("\tldc "); // int
                }

                if (parsedInt == -1) {
                    jasminString.append("m1");
                } else {
                    jasminString.append(parsedInt);
                }

            } else {
                jasminString.append("\tldc ").append(literal);
            }


        } else if (element instanceof ArrayOperand) {
            ArrayOperand operand = (ArrayOperand) element;

            jasminString.append("\taload").append(this.dealWithVarNum(operand.getName(), varTable)).append("\n"); // load array (ref)

            jasminString.append(dealWithLoadToStack(operand.getIndexOperands().get(0), varTable)); // load index
            jasminString.append("\tiaload"); // load array[index]

        } else if (element instanceof Operand) {
            Operand operand = (Operand) element;
            switch (operand.getType().getTypeOfElement()) {
                case INT32, BOOLEAN -> jasminString.append("\tiload").append(this.dealWithVarNum(operand.getName(), varTable));
                case OBJECTREF, STRING, ARRAYREF -> jasminString.append("\taload").append(this.dealWithVarNum(operand.getName(), varTable));
                case THIS -> jasminString.append("\taload_0");
                default -> jasminString.append("; ERROR: getLoadToStack() operand ").append(operand.getType().getTypeOfElement()).append("\n");
            }

        } else {
            jasminString.append("; ERROR: getLoadToStack() invalid element instance\n");
        }

        jasminString.append("\n");
        return jasminString.toString();
    }

    private String dealWithCallInstruction(CallInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminString = new StringBuilder();

        switch (instruction.getInvocationType()) {
            case invokevirtual -> {
                jasminString.append(this.dealWithLoadToStack(instruction.getFirstArg(), varTable));


                for (Element element : instruction.getListOfOperands()) {
                    jasminString.append(this.dealWithLoadToStack(element, varTable));

                }

                jasminString.append("\tinvokevirtual ")
                        .append(this.dealWithClassFullName(((ClassType) instruction.getFirstArg().getType()).getName()))
                        .append("/").append(((LiteralElement) instruction.getSecondArg()).getLiteral().replace("\"", ""))
                        .append("(");

                for (Element element : instruction.getListOfOperands()) {
                    jasminString.append(this.dealWithFieldDesc(element.getType()));
                }

                jasminString.append(")").append(this.dealWithFieldDesc(instruction.getReturnType())).append("\n");

               

            }
            case invokespecial -> {
                jasminString.append(this.dealWithLoadToStack(instruction.getFirstArg(), varTable));

                jasminString.append("\tinvokespecial ");

                if (instruction.getFirstArg().getType().getTypeOfElement() == ElementType.THIS) {
                    jasminString.append(this.superClass);
                } else {
                    String className = this.dealWithClassFullName(((ClassType) instruction.getFirstArg().getType()).getName());
                    jasminString.append(className);
                }

                jasminString.append("/").append("<init>(");

                for (Element element : instruction.getListOfOperands()) {
                    jasminString.append(this.dealWithFieldDesc(element.getType()));
                }

                jasminString.append(")").append(this.dealWithFieldDesc(instruction.getReturnType())).append("\n");

                

            }
            case invokestatic -> {

                for (Element element : instruction.getListOfOperands()) {
                    jasminString.append(this.dealWithLoadToStack(element, varTable));
                }

                jasminString.append("\tinvokestatic ")
                        .append(this.dealWithClassFullName(((Operand) instruction.getFirstArg()).getName()))
                        .append("/").append(((LiteralElement) instruction.getSecondArg()).getLiteral().replace("\"", ""))
                        .append("(");

                for (Element element : instruction.getListOfOperands()) {
                    jasminString.append(this.dealWithFieldDesc(element.getType()));
                }

                jasminString.append(")").append(this.dealWithFieldDesc(instruction.getReturnType())).append("\n");

                

            }
            case NEW -> {

                ElementType elementType = instruction.getReturnType().getTypeOfElement();

                if (elementType == ElementType.OBJECTREF) {
                    for (Element element : instruction.getListOfOperands()) {
                        jasminString.append(this.dealWithLoadToStack(element, varTable));
                    }

                    jasminString.append("\tnew ").append(this.dealWithClassFullName(((Operand) instruction.getFirstArg()).getName())).append("\n");
                } else if (elementType == ElementType.ARRAYREF) {
                    for (Element element : instruction.getListOfOperands()) {
                        jasminString.append(this.dealWithLoadToStack(element, varTable));
                    }

                    jasminString.append("\tnewarray ");
                    if (instruction.getListOfOperands().get(0).getType().getTypeOfElement() == ElementType.INT32) {
                        jasminString.append("int\n");
                    } else {
                        jasminString.append("; only int arrays are implemented\n");
                    }

                } else {
                    jasminString.append("; ERROR: NEW invocation type not implemented\n");
                }
            }
            case arraylength -> {
                jasminString.append(this.dealWithLoadToStack(instruction.getFirstArg(), varTable));
                jasminString.append("\tarraylength\n");
            }
            case ldc -> jasminString.append(this.dealWithLoadToStack(instruction.getFirstArg(), varTable));
            default -> jasminString.append("; ERROR: call instruction not implemented\n");
        }


        return jasminString.toString();
    }

    private String dealWithAssignInstruction(AssignInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminString = new StringBuilder();

        Operand dest = (Operand) instruction.getDest();
        if (dest instanceof ArrayOperand arrayOperand) {
            jasminString.append("\taload").append(this.dealWithVarNum(arrayOperand.getName(), varTable)).append("\n"); // load array (ref)
            jasminString.append(this.dealWithLoadToStack(arrayOperand.getIndexOperands().get(0), varTable)); // load index

        } else {
            // "iinc" instruction selection
            if (instruction.getRhs().getInstType() == BINARYOPER) {
                BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) instruction.getRhs();

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

        jasminString.append(this.dealWithInstructions(instruction.getRhs(), varTable));
        jasminString.append(this.dealWithStore(dest, varTable)); // store in array[index] if (dest instanceof ArrayOperand)

        return jasminString.toString();
    }

    private String dealWithStore(Operand dest, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminString = new StringBuilder();

        switch (dest.getType().getTypeOfElement()) {
            // BOOLEAN is represented as int in JVM
            case INT32, BOOLEAN -> {
                if (varTable.get(dest.getName()).getVarType().getTypeOfElement() == ElementType.ARRAYREF) {
                    jasminString.append("\tiastore").append("\n");
                } else {
                    jasminString.append("\tistore").append(this.dealWithVarNum(dest.getName(), varTable)).append("\n");
                }
            }
            case OBJECTREF, THIS, STRING, ARRAYREF -> jasminString.append("\tastore").append(this.dealWithVarNum(dest.getName(), varTable)).append("\n");
            default -> jasminString.append("; ERROR: getStore()\n");
        }

        return jasminString.toString();
    }

    private String dealWithVarNum(String name, HashMap<String, Descriptor> varTable) {
        if (name.equals("this")) {
            return "_0";
        }

        int virtualReg = varTable.get(name).getVirtualReg();

        StringBuilder jasminString = new StringBuilder();

        // virtual reg 0, 1, 2, 3 have specific operation
        if (virtualReg < 4) jasminString.append("_");
        else jasminString.append(" ");

        jasminString.append(virtualReg);

        return jasminString.toString();
    }

    private String dealWithFieldDesc(Type type) {
        StringBuilder jasminString = new StringBuilder();
        ElementType elementType = type.getTypeOfElement();

        if (elementType == ElementType.ARRAYREF) {
            jasminString.append("[");
            //noinspection deprecation
            elementType = ((ArrayType) type).getArrayType();
        }

        switch (elementType) {
            case INT32 -> jasminString.append("I");
            case BOOLEAN -> jasminString.append("Z");
            case OBJECTREF -> {
                assert type instanceof ClassType;
                String name = ((ClassType) type).getName();
                jasminString.append("L").append(this.dealWithClassFullName(name)).append(";");
            }
            case STRING -> jasminString.append("Ljava/lang/String;");
            case VOID -> jasminString.append("V");
            default -> jasminString.append("; ERROR: descriptor type not implemented\n");
        }

        return jasminString.toString();
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

    private String dealWithBoolOpResultStack() {
        return " TRUE" + this.conditionalNumber + "\n"
                + "\ticonst_0\n"
                + "\tgoto NEXT" + this.conditionalNumber + "\n"
                + "TRUE" + this.conditionalNumber + ":\n"
                + "\ticonst_1\n"
                + "NEXT" + this.conditionalNumber++ + ":";
    }



}