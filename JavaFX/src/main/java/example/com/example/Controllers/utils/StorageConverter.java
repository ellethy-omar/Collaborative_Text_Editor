// File: StorageConverter.java
package example.com.example.Controllers.utils;

import example.com.example.Controllers.CRDT.OperationEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StorageConverter {

    private StorageConverter() {
        // no instances
    }

    @SuppressWarnings("unchecked")
    public static List<OperationEntry> convertStorageToOperationEntries(Object storageObj) {
        List<OperationEntry> operations = new ArrayList<>();

        if (!(storageObj instanceof List)) {
            return operations;
        }

        for (Object item : (List<Object>) storageObj) {
            if (!(item instanceof Map)) continue;

            Map<String, Object> opMap = (Map<String, Object>) item;
            String operation = (String) opMap.get("operation");
            String charStr   = (String) opMap.get("character");
            char character   = (charStr != null && !charStr.isEmpty())
                    ? charStr.charAt(0)
                    : '\0';

            Object[] userID   = extractIdArray(opMap.get("userID"));
            Object[] parentID = extractIdArray(opMap.get("parentID"));

            OperationEntry entry = new OperationEntry(operation, character, userID);
            entry.setParentID(parentID);
            operations.add(entry);
        }

        return operations;
    }

    @SuppressWarnings("unchecked")
    public static Object[] extractIdArray(Object idObj) {
        if (idObj == null) {
            return null;
        }
        if (idObj instanceof Object[]) {
            return (Object[]) idObj;
        }
        if (idObj instanceof List) {
            List<Object> list = (List<Object>) idObj;
            return list.toArray(new Object[0]);
        }
        if (idObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) idObj;
            Object[] result = new Object[map.size()];
            for (int i = 0; i < map.size(); i++) {
                result[i] = map.get(String.valueOf(i));
            }
            return result;
        }
        return new Object[]{idObj};
    }

    @SuppressWarnings("unchecked")
    public static List<OperationEntry> convertToOperationEntries(List<Map<String, Object>> maps) {
        List<OperationEntry> entries = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            String operation = (String) map.get("operation");
            String charStr   = (String) map.get("character");
            char character   = (charStr != null && !charStr.isEmpty())
                    ? charStr.charAt(0)
                    : '\0';

            Object[] userID   = extractIdArray(map.get("userID"));
            Object[] parentID = extractIdArray(map.get("parentID"));

            OperationEntry entry = new OperationEntry(operation, character, userID);
            entry.setParentID(parentID);
            entries.add(entry);
        }
        return entries;
    }


    @SuppressWarnings("unchecked")
    public static int calculateNewCaretPosition(
            String oldText,
            String newText,
            int oldCaret)
    {
        int diffIndex = 0;
        int oldLen = oldText.length();
        int newLen = newText.length();

        while (diffIndex < oldLen && diffIndex < newLen
                && oldText.charAt(diffIndex) == newText.charAt(diffIndex)) {
            diffIndex++;
        }

        if (oldLen < newLen) {
            int insertLength = newLen - oldLen;
            if (oldCaret >= diffIndex) {
                return oldCaret + insertLength;
            }
        } else if (oldLen > newLen) {
            int deleteLength = oldLen - newLen;
            if (oldCaret >= diffIndex + deleteLength) {
                return oldCaret - deleteLength;
            } else if (oldCaret > diffIndex) {
                return diffIndex;
            }
        }

        return Math.min(oldCaret, newLen);
    }
}
