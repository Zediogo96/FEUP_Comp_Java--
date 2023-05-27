package pt.up.fe.comp2023.ollir.optimizations;

import java.util.HashMap;
import java.util.Map;

public class ConstPropParameters {
    private final Map<String, String> constants;
    private boolean isToRemoveAssigned;

    public ConstPropParameters() {
        this.constants = new HashMap<>();
        this.isToRemoveAssigned = false;
    }

    public ConstPropParameters(ConstPropParameters constPropPar) {
        this.constants = new HashMap<>(constPropPar.getConstants());
        this.isToRemoveAssigned = constPropPar.toRemoveAssigned();
    }

    public Map<String, String> getConstants() {
        return constants;
    }

    public boolean toRemoveAssigned() {
        return isToRemoveAssigned;
    }

    public void setToRemoveAssigned(boolean toRemAssigned) {
        isToRemoveAssigned = toRemAssigned;
    }

}