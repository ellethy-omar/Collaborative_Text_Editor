package example.com.example.Controllers.CRDT;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;



public class OperationEntry {
    private String operation;
    private char character;
    // private int position;
    private Object[] userID;
    private Object[] parentID;

    public OperationEntry(String operation, char character, Object[] userID) {
        this.operation = operation;
        this.character = character;
        // this.position = position;
        this.userID = userID;
    }

    public String getOperation() { return operation; }
    public char getCharacter() { return character; }
    // public int getPosition() { return position; }
    public Object[] getUserID() { return userID; }
    public Object[] getParentID() { return parentID; }

    public void setParentID(Object[] parentID) {
        this.parentID = parentID;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("operation", operation);
        map.put("character", String.valueOf(character));
        // map.put("position", String.valueOf(position));
        map.put("userID", userID);
        map.put("parentID", parentID);
        return map;
    }

    @Override
    public String toString() {
        return String.format("{operation=%s, character=%c, " +
                             "userID=%s, parentID=%s}",
            operation,
            character,
            // position,
            Arrays.toString(userID),
            parentID != null ? Arrays.toString(parentID) : "null"
        );
    }
}