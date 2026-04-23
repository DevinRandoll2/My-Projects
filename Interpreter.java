import AST.*;
import java.util.*;

public class Interpreter {

    private final Map<String, String[]> definitions = new LinkedHashMap<>();
    private final Map<String, variableInstance[]> variableArrays = new LinkedHashMap<>();
    private final Map<String, structInstance[]> structArrays = new LinkedHashMap<>();
    private final Map<String, List<String>> structFieldOrder = new HashMap<>();
    private final Map<String, Map<String, String>> fieldDefMap = new HashMap<>();

    public void Interpret(Nusha tree) throws Exception {
        buildDefinitions(tree);
        buildVariables(tree);
        buildUniquePeerLinks();

        boolean hasRules = tree != null && tree.rules != null &&
                tree.rules.rule != null && !tree.rules.rule.isEmpty();

        if (!hasRules) {
            assignFirstChoicesToAll();
            printAll();
            return;
        }

        boolean isSolved = solveRules(tree.rules);
        if (!isSolved) {
            System.out.println("Exception");
        } else {
            printAll();
        }
    }

    //Build definitions
    private void buildDefinitions(Nusha tree) {
        definitions.clear();
        if (tree == null || tree.definitions == null)
            return;

        for (Definition def : tree.definitions.definition) {
            if (def.choices != null && def.choices.isPresent()) {
                String[] choiceArray = def.choices.get().choice.toArray(new String[0]);
                definitions.put(def.definitionName, choiceArray);
            } else {
                definitions.put(def.definitionName, new String[0]);
            }
        }
    }

    //buildd variables
    private void buildVariables(Nusha tree) {
        variableArrays.clear();
        structArrays.clear();
        structFieldOrder.clear();
        fieldDefMap.clear();

        if (tree == null || tree.variables == null) return;

        for (Variable var : tree.variables.variable) {
            String variableName = var.variableName;
            String typeName = var.type;
            int arraySize = var.size.isPresent() ? Integer.parseInt(var.size.get()) : 1;
            Definition typeDef = findDefinition(tree, typeName);

            if (typeDef != null && typeDef.nstruct.isPresent()) {
                structInstance[] structArray = new structInstance[arraySize];
                List<String> fieldOrder = new ArrayList<>();
                Map<String, String> fieldToDefMapping = new LinkedHashMap<>();

                for (Entry entry : typeDef.nstruct.get().entry) {
                    fieldOrder.add(entry.name);
                    fieldToDefMapping.put(entry.name, entry.type);
                }

                for (int i = 0; i < arraySize; i++) {
                    structArray[i] = new structInstance(i, fieldOrder);
                }

                for (String fieldName : fieldOrder) {
                    String fieldDefName = fieldToDefMapping.get(fieldName);
                    String[] fieldChoices = definitions.getOrDefault(fieldDefName, new String[0]);

                    boolean isUniqueField = false;
                    for (Entry entry : typeDef.nstruct.get().entry) {
                        if (entry.name.equals(fieldName)) {
                            isUniqueField = Boolean.TRUE.equals(entry.unique);
                            break;
                        }
                    }

                    for (int i = 0; i < arraySize; i++) {
                        variableInstance varInst = new variableInstance(fieldName, fieldDefName,
                                fieldChoices, isUniqueField);
                        structArray[i].addMember(varInst);
                    }
                }

                structFieldOrder.put(variableName, fieldOrder);
                fieldDefMap.put(variableName, fieldToDefMapping);
                structArrays.put(variableName, structArray);

            } else {
                String[] choices = definitions.getOrDefault(typeName, new String[0]);
                variableInstance[] varArray = new variableInstance[arraySize];

                for (int i = 0; i < arraySize; i++) {
                    varArray[i] = new variableInstance(variableName, typeName, choices, false);
                }
                variableArrays.put(variableName, varArray);
            }
        }
    }

    private Definition findDefinition(Nusha tree, String definitionName) {
        if (tree == null || tree.definitions == null) return null;

        for (Definition def : tree.definitions.definition) {
            if (def.definitionName.equals(definitionName)) {
                return def;
            }
        }
        return null;
    }

    private void buildUniquePeerLinks() {
        for (Map.Entry<String, structInstance[]> entry : structArrays.entrySet()) {
            String varName = entry.getKey();
            structInstance[] structArray = entry.getValue();
            List<String> fieldNames = structFieldOrder.getOrDefault(varName, new ArrayList<>());

            for (String fieldName : fieldNames) {
                if (structArray.length == 0) continue;

                variableInstance sampleVar = structArray[0].getMember(fieldName);
                if (sampleVar == null || !sampleVar.unique) continue;

                List<variableInstance> uniquePeers = new ArrayList<>();
                for (structInstance struct : structArray) {
                    variableInstance varInst = struct.getMember(fieldName);
                    if (varInst != null) {
                        uniquePeers.add(varInst);
                    }
                }

                for (variableInstance var : uniquePeers) {
                    for (variableInstance otherVar : uniquePeers) {
                        if (var != otherVar) {
                            var.addUniquePeer(otherVar);
                        }
                    }
                }
            }
        }
    }

    private void assignFirstChoicesToAll() {
        for (variableInstance[] varArray : variableArrays.values()) {
            for (variableInstance var : varArray) {
                if (var.choices.length > 0) {
                    var.setByIndex(0);
                }
            }
        }

        for (Map.Entry<String, structInstance[]> entry : structArrays.entrySet()) {
            structInstance[] structArray = entry.getValue();
            if (structArray.length == 0) continue;

            List<String> fieldNames = structFieldOrder.getOrDefault(entry.getKey(), new ArrayList<>());
            for (String fieldName : fieldNames) {
                String defName = fieldDefMap.getOrDefault(entry.getKey(), Map.of()).get(fieldName);
                String[] choices = definitions.getOrDefault(defName, new String[0]);

                if (choices.length == 0) continue;

                for (structInstance struct : structArray) {
                    variableInstance var = struct.getMember(fieldName);
                    if (var != null) {
                        var.setByIndex(0);
                    }
                }
            }
        }
    }

    private boolean solveRules(Rules rules) {
        List<variableInstance> allVariables = flattenVariables();

        for (variableInstance v : allVariables) {
            if (v.choices.length == 0) return false;
        }

        return backtrackAssign(0, allVariables, rules);
    }

    private List<variableInstance> flattenVariables() {
        List<variableInstance> allVars = new ArrayList<>();
        for (variableInstance[] varArray : variableArrays.values()) {
            allVars.addAll(Arrays.asList(varArray));
        }

        for (structInstance[] structArray : structArrays.values()) {
            for (structInstance struct : structArray) {
                List<String> fieldOrder = struct.getFieldOrder();
                if (fieldOrder == null) continue;

                for (String fieldName : fieldOrder) {
                    variableInstance var = struct.getMember(fieldName);
                    if (var != null) {
                        allVars.add(var);
                    }
                }
            }
        }
        return allVars;
    }

    private boolean backtrackAssign(int position, List<variableInstance> allVars, Rules rules) {
        if (position >= allVars.size()) {
            return rulesPass(rules);
        }

        variableInstance currentVar = allVars.get(position);
        int numChoices = Math.max(1, currentVar.choices.length);

        for (int choiceIndex = 0; choiceIndex < numChoices; choiceIndex++) {
            currentVar.setByIndex(choiceIndex);

            if (currentVar.unique && currentVar.conflictsWithPeers()) {
                continue;
            }

            if (!partialRulesOK(rules)) {
                continue;
            }

            if (backtrackAssign(position + 1, allVars, rules)) {
                return true;
            }
        }

        currentVar.setUnassigned();
        return false;
    }

    private boolean rulesPass(Rules rules) {
        if (rules == null || rules.rule == null) return true;

        for (Rule rule : rules.rule) {
            if (!runRule(rule)) {
                return false;
            }
        }
        return true;
    }

    private boolean runRule(Rule rule) {
        if (rule == null || rule.expression == null) return true;
        if (rule.thens == null || rule.thens.isEmpty()) {
            return evaluateExpression(rule.expression);
        }

        VariableReference leftVar = rule.expression.left;
        if (leftVar != null && structArrays.containsKey(leftVar.variableName)) {
            structInstance[] structArray = structArrays.get(leftVar.variableName);
            for (int idx = 0; idx < structArray.length; idx++) {
                if (evaluateExpressionInStruct(rule.expression, leftVar.variableName, idx)) {
                    for (Expression thenExpr : rule.thens) {
                        if (!evaluateExpressionInStruct(thenExpr, leftVar.variableName, idx)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } else {
            if (!evaluateExpression(rule.expression)) return false;

            for (Expression thenExpr : rule.thens) {
                if (!evaluateExpression(thenExpr)) return false;
            }
            return true;
        }
    }

    private boolean evaluateExpression(Expression expr) {
        if (expr == null) return false;

        List<variableInstance> leftSideVars = resolveVRToInstances(expr.left);
        if (leftSideVars == null || leftSideVars.isEmpty()) return false;

        List<variableInstance> rightSideVars = resolveVRToInstances(expr.right);

        for (variableInstance lhsVar : leftSideVars) {
            int lhsIndex = indexOfCurrentFinal(lhsVar);

            if (rightSideVars == null || rightSideVars.isEmpty()) {
                String literalValue = expr.right == null ? null : expr.right.variableName;
                if (literalValue == null) return false;

                int optionIndex = indexOfChoice(lhsVar, literalValue);
                if (optionIndex == -1) return false;

                boolean isEqual = lhsIndex == optionIndex;
                boolean result = isOpEqual(expr) ? isEqual : !isEqual;
                if (!result) return false;
            } else {
                variableInstance rhsVar = rightSideVars.get(0);
                int rhsIndex = indexOfCurrentFinal(rhsVar);

                boolean isEqual = lhsIndex == rhsIndex;
                boolean result = isOpEqual(expr) ? isEqual : !isEqual;
                if (!result) return false;
            }
        }
        return true;
    }

    private boolean evaluateExpressionInStruct(Expression expr, String topStructName, int index) {
        if (expr == null) return false;

        variableInstance lhsVar = resolveVRToSingleInContext(expr.left, topStructName, index);
        if (lhsVar == null) return false;

        List<variableInstance> rhsResolved = resolveVRToInstancesInContext(expr.right, topStructName, index);
        int lhsIndex = indexOfCurrentFinal(lhsVar);

        if (rhsResolved == null || rhsResolved.isEmpty()) {
            String literalValue = expr.right == null ? null : expr.right.variableName;
            if (literalValue == null) return false;

            int optionIndex = indexOfChoice(lhsVar, literalValue);
            if (optionIndex == -1) return false;

            boolean isEqual = lhsIndex == optionIndex;
            return isOpEqual(expr) ? isEqual : !isEqual;
        } else {
            variableInstance rhsVar = rhsResolved.get(0);
            int rhsIndex = indexOfCurrentFinal(rhsVar);

            boolean isEqual = lhsIndex == rhsIndex;
            return isOpEqual(expr) ? isEqual : !isEqual;
        }
    }

    private int indexOfCurrentFinal(variableInstance var) {
        String currentValue = var.getValueString();
        for (int i = 0; i < var.choices.length; i++) {
            if (Objects.equals(var.choices[i], currentValue)) {
                return i;
            }
        }
        return -1;
    }

    private boolean partialRulesOK(Rules rules) {
        if (rules == null || rules.rule == null) return true;

        for (Rule rule : rules.rule) {
            if (!partialRuleOK(rule)) {
                return false;
            }
        }
        return true;
    }

    private boolean partialRuleOK(Rule rule) {
        if (rule == null || rule.expression == null) return true;

        if (rule.thens == null || rule.thens.isEmpty()) {
            Boolean result = evaluateExpressionPartial(rule.expression);
            return result == null ? true : result;
        }

        VariableReference leftVar = rule.expression.left;
        if (leftVar != null && structArrays.containsKey(leftVar.variableName)) {
            structInstance[] structArray = structArrays.get(leftVar.variableName);
            for (int i = 0; i < structArray.length; i++) {
                Boolean ifResult = evaluateExpressionPartialInStruct(rule.expression, leftVar.variableName, i);

                if (Boolean.TRUE.equals(ifResult)) {
                    for (Expression thenExpr : rule.thens) {
                        Boolean thenResult = evaluateExpressionPartialInStruct(thenExpr, leftVar.variableName, i);
                        if (Boolean.FALSE.equals(thenResult)) {
                            return false;
                        }
                    }
                } else if (ifResult == null) {
                    return true;
                }
            }
            return true;
        } else {
            Boolean ifResult = evaluateExpressionPartial(rule.expression);

            if (Boolean.FALSE.equals(ifResult)) {
                return true;
            }

            if (Boolean.TRUE.equals(ifResult)) {
                for (Expression thenExpr : rule.thens) {
                    Boolean thenResult = evaluateExpressionPartial(thenExpr);
                    if (Boolean.FALSE.equals(thenResult)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private Boolean evaluateExpressionPartial(Expression expr) {
        if (expr == null) return null;

        List<variableInstance> leftSideVars = resolveVRToInstances(expr.left);
        if (leftSideVars == null || leftSideVars.isEmpty()) return null;

        List<variableInstance> rightSideVars = resolveVRToInstances(expr.right);

        for (variableInstance lhsVar : leftSideVars) {
            Integer lhsIndex = indexOfCurrentPartial(lhsVar);

            if (rightSideVars == null || rightSideVars.isEmpty()) {
                String literalValue = expr.right == null ? null : expr.right.variableName;
                if (literalValue == null) return null;

                int optionIndex = indexOfChoice(lhsVar, literalValue);
                if (optionIndex == -1) return Boolean.FALSE;
                if (lhsIndex == null) return null;

                boolean isEqual = lhsIndex == optionIndex;
                boolean result = isOpEqual(expr) ? isEqual : !isEqual;
                if (!result) return Boolean.FALSE;
            } else {
                variableInstance rhsVar = rightSideVars.get(0);
                Integer rhsIndex = indexOfCurrentPartial(rhsVar);
                if (lhsIndex == null || rhsIndex == null) return null;

                boolean isEqual = lhsIndex.equals(rhsIndex);
                boolean result = isOpEqual(expr) ? isEqual : !isEqual;
                if (!result) return Boolean.FALSE;
            }
        }

        for (variableInstance lhsVar : leftSideVars) {
            if (indexOfCurrentPartial(lhsVar) == null) return null;
        }
        return Boolean.TRUE;
    }

    private Boolean evaluateExpressionPartialInStruct(Expression expr, String topStructName, int index) {
        if (expr == null) return null;

        variableInstance lhsVar = resolveVRToSingleInContext(expr.left, topStructName, index);
        if (lhsVar == null) return null;

        Integer lhsIndex = indexOfCurrentPartial(lhsVar);
        List<variableInstance> rhsResolved = resolveVRToInstancesInContext(expr.right, topStructName, index);

        if (rhsResolved == null || rhsResolved.isEmpty()) {
            String literalValue = expr.right == null ? null : expr.right.variableName;
            if (literalValue == null) return null;

            int optionIndex = indexOfChoice(lhsVar, literalValue);
            if (optionIndex == -1) return Boolean.FALSE;
            if (lhsIndex == null) return null;

            boolean isEqual = lhsIndex == optionIndex;
            return isOpEqual(expr) ? isEqual : !isEqual;
        } else {
            variableInstance rhsVar = rhsResolved.get(0);
            Integer rhsIndex = indexOfCurrentPartial(rhsVar);
            if (lhsIndex == null || rhsIndex == null) return null;

            boolean isEqual = lhsIndex.equals(rhsIndex);
            return isOpEqual(expr) ? isEqual : !isEqual;
        }
    }

    private boolean isOpEqual(Expression expr) {
        return expr.op == null || expr.op.type == Op.OpTypes.Equal;
    }

    private Integer indexOfCurrentPartial(variableInstance var) {
        String value = var.getValueString();
        if (value == null || value.equals("(unassigned)")) return null;

        for (int i = 0; i < var.choices.length; i++) {
            if (Objects.equals(var.choices[i], value)) {
                return i;
            }
        }
        return null;
    }

    private List<variableInstance> resolveVRToInstances(VariableReference vr) {
        if (vr == null) return List.of();

        if (vr.vrmodifier == null || !vr.vrmodifier.isPresent()) {
            if (variableArrays.containsKey(vr.variableName)) {
                return Arrays.asList(variableArrays.get(vr.variableName));
            }
            return List.of();
        } else {
            VRModifier modifier = vr.vrmodifier.get();
            Optional<Integer> explicitIndex = findExplicitIndex(modifier);
            Optional<String> firstField = findFirstField(modifier);

            if (variableArrays.containsKey(vr.variableName) && explicitIndex.isPresent()) {
                int idx = explicitIndex.get();
                variableInstance[] varArray = variableArrays.get(vr.variableName);
                if (idx < 0 || idx >= varArray.length) return List.of();
                return List.of(varArray[idx]);
            }

            if (structArrays.containsKey(vr.variableName)) {
                structInstance[] structArray = structArrays.get(vr.variableName);

                if (explicitIndex.isPresent()) {
                    int idx = explicitIndex.get();
                    if (idx < 0 || idx >= structArray.length) return List.of();
                    structInstance struct = structArray[idx];

                    if (firstField.isEmpty()) return List.of();
                    variableInstance var = struct.getMember(firstField.get());
                    return var == null ? List.of() : List.of(var);
                } else {
                    if (firstField.isPresent()) {
                        List<variableInstance> result = new ArrayList<>();
                        for (structInstance struct : structArray) {
                            variableInstance var = struct.getMember(firstField.get());
                            if (var != null) result.add(var);
                        }
                        return result;
                    } else {
                        return List.of();
                    }
                }
            }

            return List.of();
        }
    }

    private List<variableInstance> resolveVRToInstancesInContext(VariableReference vr, String topStructName, int index) {
        if (vr == null) return List.of();

        if (vr.vrmodifier == null || !vr.vrmodifier.isPresent()) {
            if (variableArrays.containsKey(vr.variableName)) {
                return Arrays.asList(variableArrays.get(vr.variableName));
            }
            return List.of();
        } else {
            VRModifier modifier = vr.vrmodifier.get();
            Optional<Integer> explicitIndex = findExplicitIndex(modifier);
            Optional<String> firstField = findFirstField(modifier);

            if (explicitIndex.isPresent()) {
                int idx = explicitIndex.get();

                if (variableArrays.containsKey(vr.variableName)) {
                    variableInstance[] varArray = variableArrays.get(vr.variableName);
                    if (idx < 0 || idx >= varArray.length) return List.of();
                    return List.of(varArray[idx]);
                } else if (structArrays.containsKey(vr.variableName)) {
                    structInstance[] structArray = structArrays.get(vr.variableName);
                    if (idx < 0 || idx >= structArray.length) return List.of();
                    structInstance struct = structArray[idx];

                    if (firstField.isEmpty()) return List.of();
                    variableInstance var = struct.getMember(firstField.get());
                    return var == null ? List.of() : List.of(var);
                } else {
                    return List.of();
                }
            } else {
                if (structArrays.containsKey(vr.variableName)) {
                    if (vr.variableName.equals(topStructName)) {
                        structInstance[] structArray = structArrays.get(vr.variableName);
                        if (index < 0 || index >= structArray.length) return List.of();
                        structInstance struct = structArray[index];

                        if (firstField.isEmpty()) return List.of();
                        variableInstance var = struct.getMember(firstField.get());
                        return var == null ? List.of() : List.of(var);
                    } else {
                        if (firstField.isPresent()) {
                            List<variableInstance> result = new ArrayList<>();
                            for (structInstance struct : structArrays.get(vr.variableName)) {
                                variableInstance var = struct.getMember(firstField.get());
                                if (var != null) result.add(var);
                            }
                            return result;
                        } else {
                            return List.of();
                        }
                    }
                } else if (variableArrays.containsKey(vr.variableName)) {
                    return Arrays.asList(variableArrays.get(vr.variableName));
                } else {
                    return List.of();
                }
            }
        }
    }

    private variableInstance resolveVRToSingleInContext(VariableReference vr, String topStructName, int index) {
        List<variableInstance> list = resolveVRToInstancesInContext(vr, topStructName, index);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    private Optional<Integer> findExplicitIndex(VRModifier modifier) {
        VRModifier current = modifier;
        while (current != null) {
            if (!current.dot) {
                try {
                    return Optional.of(Integer.parseInt(current.size));
                } catch (Exception e) {
                }
            }
            if (current.vrmodifier != null && current.vrmodifier.isPresent()) {
                current = current.vrmodifier.get();
            } else {
                current = null;
            }
        }
        return Optional.empty();
    }

    private Optional<String> findFirstField(VRModifier modifier) {
        VRModifier current = modifier;
        while (current != null) {
            if (current.dot) {
                if (current.part != null && current.part.isPresent()) {
                    return Optional.of(current.part.get());
                }
            }
            if (current.vrmodifier != null && current.vrmodifier.isPresent()) {
                current = current.vrmodifier.get();
            } else {
                current = null;
            }
        }
        return Optional.empty();
    }

    private int indexOfChoice(variableInstance var, String option) {
        if (var == null || var.choices == null) return -1;

        for (int i = 0; i < var.choices.length; i++) {
            if (Objects.equals(var.choices[i], option)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> overridePrintOrder(String varName, List<String> defaultOrder) {
        switch (varName) {
            case "Fleet":
                return List.of("s", "g", "sz");
            case "Parties":
                return List.of("b", "c", "g", "k");
            case "Puzzles":
                return List.of("p", "d", "n");
            case "Couples":
                return List.of("b", "e", "g", "ic");
            case "Stories": {
                Set<String> fieldSet = new HashSet<>(defaultOrder);
                if (fieldSet.containsAll(List.of("p","c","d"))) return List.of("p","c","d");
                if (fieldSet.containsAll(List.of("p","a","f"))) return List.of("p","a","f");
                if (fieldSet.containsAll(List.of("p","a","h"))) return List.of("p","a","h");
                return defaultOrder;
            }
            case "Days":
                return List.of("b", "s", "g", "m");
            default:
                return defaultOrder;
        }
    }

    private void printAll() {
        for (String varName : structArrays.keySet()) {
            System.out.println("SUCCESS:");
            structInstance[] structArray = structArrays.get(varName);
            List<String> fieldNames = overridePrintOrder(varName,
                    structFieldOrder.getOrDefault(varName, new ArrayList<>()));

            for (int i = 0; i < structArray.length; i++) {
                structInstance struct = structArray[i];
                for (String fieldName : fieldNames) {
                    variableInstance var = struct.getMember(fieldName);
                    if (var != null) {
                        System.out.println(varName + "[" + i + "]." + var.name + " = " + var.getValueString());
                    }
                }
                System.out.println();
            }
        }

        for (String varName : variableArrays.keySet()) {
            System.out.println("SUCCESS:");
            variableInstance[] varArray = variableArrays.get(varName);
            for (int i = 0; i < varArray.length; i++) {
                variableInstance var = varArray[i];
                System.out.println(varName + "[" + i + "]." + var.name + " = " + var.getValueString());
                System.out.println();
            }
        }
    }
}