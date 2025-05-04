package example.com.example.Controllers.CRDT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TreeNode {
    private final Object[] userID;
    private final char value;
    private boolean tombstone = false;
    private final List<TreeNode> children = new ArrayList<>();

    public TreeNode(Object[] userID, char value) {
        this.userID = userID;
        this.value = value;
    }

    public Object[] getUserID() {
        return userID;
    }

    public char getValue() {
        return value;
    }

    public boolean isTombstone() {
        return tombstone;
    }

    public void setTombstone(boolean tombstone) {
        this.tombstone = tombstone;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public String getIdString() {
        return Arrays.toString(userID);
    }

    public long getTimestamp() {
        return Long.parseLong(userID[1].toString());
    }
}