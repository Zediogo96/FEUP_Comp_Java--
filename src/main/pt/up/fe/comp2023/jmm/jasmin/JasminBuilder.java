package pt.up.fe.comp2023.jmm.jasmin;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;

public class JasminBuilder implements JasminBackend {

    ClassUnit classUnit;

    String superClass;
    int current;

    final int localLimit = 99;
    final int stackLimit = 99;

    int countStack;

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        classUnit = ollirResult.getOllirClass();
        try {
            classUnit.checkMethodLabels();
        } catch (OllirErrorException e) {
            return new JasminResult(classUnit.getClassName(), null,
                    Collections.singletonList(Report.newError(Stage.GENERATION, -1,
                            -1, "Jasmin Exception\n", e)));
        }

        classUnit.buildCFGs();
        classUnit.buildVarTables();

        String code = builder();

        if (ollirResult.getConfig().getOrDefault("debug", "false").equals("true")) {
            System.out.println("JASMIN CODE:");
            System.out.println(code);
        }

        //System.out.println(code);


        return new JasminResult(ollirResult, code, Collections.emptyList());
    }

    private String builder() {
        StringBuilder code = new StringBuilder();

        code.append(".class public ").append(classUnit.getClassName()).append("\n");

        superClass = classUnit.getSuperClass();

        if (superClass == null) superClass = "java/lang/Object";

        code.append(".super ").append(this.getImpClass(superClass)).append("\n\n");

        for (Field field : classUnit.getFields()) {

            AccessModifiers accessModifiers = field.getFieldAccessModifier();
            StringBuilder accessModName =  new StringBuilder();

            if (accessModifiers != AccessModifiers.DEFAULT) {
                accessModName.append(accessModifiers.name().toLowerCase()).append(" ");
            }

            if (field.isStaticField()) accessModName.append("static ");

            if (field.isFinalField()) accessModName.append("final ");

            code.append(".field ").append(accessModName).append(field.getFieldName()).append(" ").append(this.getType(field.getFieldType()));

            if (field.isInitialized()) code.append(" = ").append(field.getInitialValue());

            code.append("\n");
        }

        for (Method method : this.classUnit.getMethods()) {
            code.append(this.getMethod(method));
        }

        return code.toString();
    }

    private String getImpClass(String className) {
        if (className.equals("this")) {
            return classUnit.getClassName();
        }
        for (String imp : classUnit.getImports()) {
            if (imp.endsWith(className)) return imp.replaceAll("\\.", "/");
        }

        return className;
    }

    private String getType(Type type) {
        StringBuilder variable = new StringBuilder();
        ElementType elementType = type.getTypeOfElement();

        if (elementType == ElementType.ARRAYREF) {
            variable.append("[");
            elementType = ((ArrayType) type).getArrayType();
        }

        switch (elementType) {
            case VOID ->  variable.append("V");
            case INT32 -> variable.append("I");
            case STRING -> variable.append("Ljava/lang/String;");
            case BOOLEAN -> variable.append("Z");
            case OBJECTREF -> {
                assert type instanceof ClassType;
                variable.append("L").append(this.getImpClass(((ClassType) type).getName())).append(";");
            }
            default -> variable.append("Error: type not supported;\n");
        }

        return variable.toString();
    }

    private String getMethod(Method method) {
        StringBuilder met = new StringBuilder();
        AccessModifiers accessModifiers = method.getMethodAccessModifier();

        met.append(".method ");
        if (accessModifiers != AccessModifiers.DEFAULT) {
            met.append(accessModifiers.name().toLowerCase()).append(" ");
        }

        if (method.isStaticMethod()) met.append("static ");
        if (method.isFinalMethod()) met.append("final ");
        if (method.isConstructMethod()) met.append("<init>");
        else met.append(method.getMethodName());

        met.append("(");

        for (Element element : method.getParams()) {
            met.append(this.getType(element.getType()));
        }
        met.append(")");
        met.append(this.getType(method.getReturnType())).append("\n");

        current = 0;

        met.append("\t.limit stack " + stackLimit + "\n" + "\t.limit locals " +
                localLimit + "\n");

        List<Instruction> instructions = method.getInstructions();

        for (Instruction instruction : method.getInstructions()) {
            for (Map.Entry<String, Instruction> label : method.getLabels().entrySet()) {
                if (label.getValue().equals(instruction)) met.append(label.getKey()).append(":\n");
            }
            met.append(this.getInstruction(instruction, method.getVarTable()));

            if (instruction.getInstType() == InstructionType.CALL) {
                CallInstruction inst = (CallInstruction) instruction;
                ElementType ret = inst.getReturnType().getTypeOfElement();

                if (ret != ElementType.VOID) {
                    met.append("\tpop\n");
                    current -= 1;
                }

            }
        }

        if (!((instructions.get(instructions.size() - 1).getInstType() == InstructionType.RETURN) && (instructions.size() > 0)))
        {
            if (method.getReturnType().getTypeOfElement() == ElementType.VOID) met.append("\treturn\n");
        }

        met.append(".end method\n\n");
        return met.toString();
    }

    private String getInstruction(Instruction instruction, HashMap<String, Descriptor> varTable) {

        return switch (instruction.getInstType()) {
            case CALL -> dealWithCall((CallInstruction) instruction, varTable);
            case GOTO -> dealWithGoTo((GotoInstruction) instruction);
            case BRANCH -> dealWithBranch((CondBranchInstruction) instruction, varTable);
            case NOPER -> loadStack(((SingleOpInstruction) instruction).getSingleOperand(), varTable);
            case ASSIGN -> dealWithAssign((AssignInstruction) instruction, varTable);
            case RETURN -> dealWithReturn((ReturnInstruction) instruction, varTable);
            case GETFIELD -> dealWithGetField((GetFieldInstruction) instruction, varTable);
            case PUTFIELD -> dealWithPutField((PutFieldInstruction) instruction,  varTable);
            case UNARYOPER -> dealWithUnaryOper((UnaryOpInstruction) instruction, varTable);
            case BINARYOPER -> dealWithBinaryOper((BinaryOpInstruction) instruction, varTable);
        };
    }

    private String loadStack(Element singleOperand, HashMap<String, Descriptor> varTable) {
        StringBuilder inst = new StringBuilder();

        if (singleOperand instanceof LiteralElement) {
            String literal = ((LiteralElement) singleOperand).getLiteral();

            if ((singleOperand.getType().getTypeOfElement() == ElementType.INT32) ||
                    singleOperand.getType().getTypeOfElement() == ElementType.BOOLEAN) {

                int parser = Integer.parseInt(literal);

                if (parser >= -1 && parser <= 5) inst.append("\ticonst_");

                else if (parser >= -128 && parser <= 127) inst.append("\tbipush ");

                else if (parser >= -32768 && parser <= 32767) inst.append("\tsipush ");

                else inst.append("\tldc ");

                if (parser == -1) inst.append("m1");
                else inst.append(parser);
            }
            else inst.append("\tldc ").append(literal);

            current += 1;
        }

        else if (singleOperand instanceof ArrayOperand op) {

            inst.append("\taload").append(this.getVarRegister(op.getName(), varTable)).append("\n");

            current += 1;

            inst.append(loadStack(op.getIndexOperands().get(0), varTable));
            inst.append("\tiaload");

            current -= 1;

        }
        else if (singleOperand instanceof Operand operand) {

            switch (operand.getType().getTypeOfElement()) {
                case INT32, BOOLEAN -> inst.append("\tiload").append(this.getVarRegister(operand.getName(), varTable));
                case OBJECTREF, STRING, ARRAYREF -> inst.append("\taload").append(this.getVarRegister(operand.getName(), varTable));
                case THIS -> inst.append("\taload_0");
                default -> inst.append("Error: SingleOperand ").append(operand.getType().getTypeOfElement()).append("\n");
            }
            current += 1;
        }
        else inst.append("Error: SingleOperand not recognized\n");

        inst.append("\n");
        return inst.toString();
    }

    private String getVarRegister(String name, HashMap<String, Descriptor> varTable) {
        StringBuilder register = new StringBuilder();

        if (name.equals("this")) return "_0";

        int reg = varTable.get(name).getVirtualReg();

        if (reg < 4) register.append("_");
        else register.append(" ");

        register.append(reg);

        return register.toString();
    }

    private String dealWithBinaryOper(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder strInst = new StringBuilder();

        Element left = instruction.getLeftOperand();
        Element right = instruction.getRightOperand();

        strInst.append(this.loadStack(left, varTable))
                .append(this.loadStack(right, varTable))
                .append("\t").append(printOpType(instruction.getOperation().getOpType()));

        OperationType op = instruction.getOperation().getOpType();

        if (op == OperationType.EQ || op == OperationType.GTH
                || op == OperationType.GTE || op == OperationType.LTE
                || op == OperationType.LTH || op == OperationType.NEQ) {
            strInst.append(this.printOperation());
        }

        strInst.append("\n");

        current -= 1;

        return strInst.toString();
    }

    private String printOpType(OperationType opType) {
        return switch (opType) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case LTH -> "if_icmplt";
            case ANDB -> "iand";
            case NOTB -> "ifeq";
            case DIV -> "idiv";
            default -> "Error: operation not recognized\n";
        };
    }

    private String dealWithUnaryOper(UnaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder strInst = new StringBuilder();

        strInst.append(this.loadStack(instruction.getOperand(), varTable))
                .append("\t").append(printOpType(instruction.getOperation().getOpType()));

        if (instruction.getOperation().getOpType() == OperationType.NOTB) {
            strInst.append(this.printOperation());
        }
        else strInst.append("Error: invalid UnaryOper\n");

        strInst.append("\n");

        return strInst.toString();
    }

    private String printOperation() {
        return " TRUE" + this.countStack + "\n" + "\ticonst_0\n" +
                "\tgoto NEXT" + this.countStack + "\n" + "TRUE" +
                this.countStack + ":\n" + "\ticonst_1\n" + "NEXT" +
                this.countStack++ + ":";
    }

    private String dealWithPutField(PutFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        current -= 2;

        return loadStack(instruction.getFirstOperand(), varTable) + loadStack(instruction.getThirdOperand(), varTable) +
                "\tputfield " + getImpClass(((Operand) instruction.getFirstOperand()).getName()) +
                "/" + ((Operand) instruction.getSecondOperand()).getName() + " " + getType(instruction.getSecondOperand().getType()) + "\n";
    }

    private String dealWithGetField(GetFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        return loadStack(instruction.getFirstOperand(), varTable) + "\tgetfield " + getImpClass(((Operand) instruction.getFirstOperand()).getName()) +
                "/" + ((Operand) instruction.getSecondOperand()).getName() + " " + getType(instruction.getSecondOperand().getType()) + "\n";
    }

    private String dealWithReturn(ReturnInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder strInst = new StringBuilder();

        if (instruction.hasReturnValue()) strInst.append(loadStack(instruction.getOperand(), varTable));
        strInst.append("\t");

        if (instruction.getOperand() != null) {
            ElementType elementType = instruction.getOperand().getType().getTypeOfElement();

            if (elementType == ElementType.INT32 || elementType == ElementType.BOOLEAN) strInst.append("i");
            else strInst.append("a");
        }
        strInst.append("return\n");

        return strInst.toString();
    }

    private String dealWithAssign(AssignInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder strInst = new StringBuilder();
        Operand destination = (Operand) instruction.getDest();

        if (destination instanceof ArrayOperand opDest) {

            current += 1;
            strInst.append("\taload").append(this.getVarRegister(opDest.getName(), varTable)).append("\n")
                    .append(loadStack(opDest.getIndexOperands().get(0), varTable));
        }
        else {
            if (instruction.getRhs().getInstType() == InstructionType.BINARYOPER) {

                BinaryOpInstruction inst = (BinaryOpInstruction) instruction.getRhs();

                if (inst.getOperation().getOpType() == OperationType.ADD) {

                    LiteralElement literal = null;
                    Operand op = null;

                    if (!inst.getLeftOperand().isLiteral() && inst.getRightOperand().isLiteral()) {
                        literal = (LiteralElement) inst.getRightOperand();
                        op = (Operand) inst.getLeftOperand();
                    }

                    else if (inst.getLeftOperand().isLiteral() && !inst.getRightOperand().isLiteral()) {
                        literal = (LiteralElement) inst.getLeftOperand();
                        op = (Operand) inst.getRightOperand();
                    }

                    if (literal != null && op != null) {
                        if (op.getName().equals(destination.getName())) {

                            int literalValue = Integer.parseInt((literal).getLiteral());

                            if (literalValue >= -128 && literalValue <= 127) {
                                return "\tiinc " + varTable.get(op.getName()).getVirtualReg() + " " + literalValue + "\n";
                            }
                        }
                    }

                }
            }
        }
        strInst.append(this.getInstruction(instruction.getRhs(), varTable));

        switch (destination.getType().getTypeOfElement()) {

            case OBJECTREF, THIS, STRING, ARRAYREF -> {
                strInst.append("\tastore").append(this.getVarRegister(destination.getName(), varTable)).append("\n");
                current -= 1;
            }

            case INT32, BOOLEAN -> {
                if (varTable.get(destination.getName()).getVarType().getTypeOfElement() == ElementType.ARRAYREF) {
                    strInst.append("\tiastore").append("\n");
                    current -= 3;
                }
                else {
                    strInst.append("\tistore").append(this.getVarRegister(destination.getName(), varTable)).append("\n");
                    current -= 1;
                }
            }
            default -> strInst.append("Error: couldn't store instruction\n");
        }

        return strInst.toString();
    }

    private String dealWithBranch(CondBranchInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder strInst = new StringBuilder();
        String op;

        Instruction brancher;
        if (instruction instanceof SingleOpCondInstruction singleOpCondInstruction) {
            brancher = singleOpCondInstruction.getCondition();
        }
        else if (instruction instanceof OpCondInstruction opCondInstruction) {
            brancher = opCondInstruction.getCondition();
        }
        else {
            return "Error: Instruction branch error\n";
        }
        switch (brancher.getInstType()) {
            case UNARYOPER -> {
                assert brancher instanceof UnaryOpInstruction;
                UnaryOpInstruction unaryOpInstruction = (UnaryOpInstruction) brancher;
                if (unaryOpInstruction.getOperation().getOpType() == OperationType.NOTB) {
                    strInst.append(this.loadStack(unaryOpInstruction.getOperand(), varTable));
                    op = "ifeq";
                }
                else {
                    strInst.append("Error: invalid UnaryOper\n");
                    strInst.append(this.getInstruction(brancher, varTable));
                    op = "ifne";
                }
            }
            case BINARYOPER -> {
                assert brancher instanceof BinaryOpInstruction;
                BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) brancher;
                switch (binaryOpInstruction.getOperation().getOpType()) {
                    case LTH -> {
                        Integer counter = null;
                        Element other = null;

                        Element  left = binaryOpInstruction.getLeftOperand();
                        Element right = binaryOpInstruction.getRightOperand();
                        op = "if_icmplt";

                        if (left instanceof LiteralElement) {
                            String lit = ((LiteralElement) left).getLiteral();
                            counter = Integer.parseInt(lit);
                            other = right;
                            op = "ifgt";
                        }
                        else if (right instanceof LiteralElement) {
                            String lit = ((LiteralElement) right).getLiteral();
                            counter = Integer.parseInt(lit);
                            other = left;
                            op = "iflt";
                        }
                        if (counter != null && counter == 0) {
                            strInst.append((this.loadStack(other, varTable)));
                        }
                        else {
                            strInst.append(this.loadStack(left, varTable)).append(this.loadStack(right, varTable));
                            op = "if_icmplt";
                        }
                    }
                    case ANDB -> {
                        strInst.append(this.getInstruction(brancher, varTable));
                        op = "ifne";
                    }
                    default -> {
                        strInst.append("Error: BinaryOper not recognized\n");
                        op = "ifne";
                    }
                }
            }
            default -> {
                strInst.append(this.getInstruction(brancher, varTable));
                op = "ifne";
            }
        }
        strInst.append("\t").append(op).append(" ").append(instruction.getLabel()).append("\n");

        if (op.equals("if_icmplt")) current -= 2;
        else current -= 1;

        return strInst.toString();
    }

    private String dealWithGoTo(GotoInstruction instruction) {
        return "\tgoto " + instruction.getLabel() + "\n";
    }

    private String dealWithCall(CallInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder strInst = new StringBuilder();
        int popper = 0;

        switch (instruction.getInvocationType()) {
            case arraylength -> strInst.append(loadStack(instruction.getFirstArg(), varTable)).append("\tarraylength\n");

            case NEW -> {
                popper -= 1;
                ElementType type =instruction.getReturnType().getTypeOfElement();
                if (type == ElementType.OBJECTREF) {
                    for (Element element : instruction.getListOfOperands()) {
                        strInst.append(this.loadStack(element, varTable));
                        popper++;
                    }
                    strInst.append("\tnew ").append(getImpClass(((Operand) instruction.getFirstArg()).getName())).append("\n");
                }
                else if (type == ElementType.ARRAYREF) {
                    for (Element element : instruction.getListOfOperands()) {
                        strInst.append(this.loadStack(element, varTable));
                        popper++;
                    }
                    strInst.append("\tnewarray ");
                    if (instruction.getListOfOperands().get(0).getType().getTypeOfElement() == ElementType.INT32) strInst.append("int\n");

                    else strInst.append("Error: Array type not supported\n");

                }
                else strInst.append("Error: Type not supported\n");

            }

            case ldc -> strInst.append(loadStack(instruction.getFirstArg(), varTable));

            case invokestatic -> {

                for (Element element : instruction.getListOfOperands()) {
                    strInst.append(this.loadStack(element, varTable));
                    popper++;
                }
                strInst.append("\tinvokestatic ").append(getImpClass(((Operand) instruction.getFirstArg()).getName())).append("/").append(((LiteralElement) instruction.getSecondArg()).getLiteral().replace("\"", "")).append("(");

                for (Element element : instruction.getListOfOperands()) {
                    strInst.append(this.getType(element.getType()));
                }
                strInst.append(")").append(getType(instruction.getReturnType())).append("\n");

                if (instruction.getReturnType().getTypeOfElement() != ElementType.VOID) popper--;
            }
            case invokespecial -> {
                popper = 1;
                strInst.append(loadStack(instruction.getFirstArg(), varTable));

                strInst.append("\tinvokespecial ");

                if (instruction.getFirstArg().getType().getTypeOfElement() == ElementType.THIS) strInst.append(superClass);
                else {
                    String className = this.getImpClass(((ClassType) instruction.getFirstArg().getType()).getName());
                    strInst.append(className);
                }
                strInst.append("/" + "<init>(");

                for (Element element : instruction.getListOfOperands()) {
                    strInst.append(getType(element.getType()));
                }
                strInst.append(")").append(getType(instruction.getReturnType())).append("\n");
                if (instruction.getReturnType().getTypeOfElement() != ElementType.VOID) popper--;
            }
            case invokevirtual -> {
                popper = 1;
                strInst.append(loadStack(instruction.getFirstArg(), varTable));

                for (Element element : instruction.getListOfOperands()) {
                    strInst.append(loadStack(element, varTable));
                    popper++;
                }
                strInst.append("\tinvokevirtual ").append(getImpClass(((ClassType) instruction.getFirstArg().getType()).getName())).append("/").append(((LiteralElement) instruction.getSecondArg()).getLiteral().replace("\"", "")).append("(");

                for (Element element : instruction.getListOfOperands()) {
                    strInst.append(getType(element.getType()));
                }

                strInst.append(")").append(getType(instruction.getReturnType())).append("\n");

                if (instruction.getReturnType().getTypeOfElement() != ElementType.VOID) popper--;
            }

            default -> strInst.append("Error: call instruction not processed.");
        }

        current -= popper;

        return strInst.toString();
    }

}
