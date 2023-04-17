package pt.up.fe.comp2023.ollir;

public class OllirInference {

    private final String inferredType;
    private final Boolean isAssignedToTempVar;

    public OllirInference(String inferredType, Boolean isAssignedToTempVar) {
        this.inferredType = inferredType;
        this.isAssignedToTempVar = isAssignedToTempVar;
    }

    public OllirInference(Boolean isAssignedToTempVar) {
        this.inferredType = null;
        this.isAssignedToTempVar = isAssignedToTempVar;
    }

    public String getInferredType() {
        return inferredType;
    }

    public Boolean getIsAssignedToTempVar() {
        return isAssignedToTempVar;
    }

}

