import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class variableInstance {
    public final String name;
    public final String defName;
    public final String[] choices;
    private int valueIndex = -1;
    public final boolean unique;
    private final List<variableInstance> uniquePeers = new ArrayList<>();

    public variableInstance(String name, String defName, String[] choices, boolean unique) {
        this.name = name;
        this.defName = defName;
        this.choices = choices != null ? choices : new String[0];
        this.unique = unique;
    }

    public boolean setByName(String val) {
        int idx = indexOf(val);
        if (idx == -1) throw new IllegalArgumentException("Value not found: " + val);
        this.valueIndex = idx;
        return true;
    }

    public boolean setByIndex(int idx) {
        if (idx < 0 || idx >= choices.length) {
            throw new IllegalArgumentException("Index out of range: " + idx);
        }
        this.valueIndex = idx;
        return true;
    }

    public void setUnassigned() {
        this.valueIndex = -1;
    }

    public String getValueString() {
        return valueIndex == -1 ? "(unassigned)" : choices[valueIndex];
    }

    private int indexOf(String val) {
        for (int i = 0; i < choices.length; i++) {
            if (Objects.equals(choices[i], val)) return i;
        }
        return -1;
    }

    public void addUniquePeer(variableInstance peer) {
        if (peer == null) return;
        if (!uniquePeers.contains(peer)) uniquePeers.add(peer);
    }

    public List<variableInstance> getUniquePeers() {
        return uniquePeers;
    }

    public boolean conflictsWithPeers() {
        if (!unique) return false;
        for (variableInstance p : uniquePeers) {
            if (this.valueIndex != -1 && this.valueIndex == p.valueIndex) {
                return true;
            }
        }
        return false;
    }
}
