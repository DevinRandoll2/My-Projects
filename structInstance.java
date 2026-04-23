import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class structInstance {
    private final Map<String, variableInstance> members = new HashMap<>();
    public final int index;
    private final List<String> fieldOrder;

    public structInstance(int index, List<String> fieldOrder) {
        this.index = index;
        this.fieldOrder = fieldOrder;
    }

    public void addMember(variableInstance vi) {
        members.put(vi.name, vi);
    }

    public variableInstance getMember(String name) {
        return members.get(name);
    }

    public List<String> getFieldOrder() {
        return fieldOrder;
    }
}
