package example.com.example.Controllers.CRDT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TreeNode {
    private final Object[] userID;
    private final char value;
    private boolean tombstone = false;
    private TreeNode parent;
    private final List<TreeNode> children = new ArrayList<>();

    public TreeNode(Object[] userID, char value) {
        this.userID = userID;
        this.value = value;
    }

    public Object[] getUserID() { return userID; }
    public char getValue() { return value; }
    public boolean isTombstone() { return tombstone; }
    public void setTombstone(boolean tombstone) { this.tombstone = tombstone; }
    public TreeNode getParent() { return parent; }
    public void setParent(TreeNode parent) { this.parent = parent; }
    public List<TreeNode> getChildren() { return children; }
    public long getTimestamp() { return Long.parseLong(userID[1].toString()); }
}