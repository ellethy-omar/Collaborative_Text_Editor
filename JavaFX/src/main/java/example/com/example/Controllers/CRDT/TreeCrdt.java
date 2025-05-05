package example.com.example.Controllers.CRDT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TreeCrdt {
    private final TreeNode root;
    private final Map<String, TreeNode> nodes;

    public TreeCrdt() {
        this.root = new TreeNode(new Object[]{"root", 0L}, '\0');
        this.root.setParent(null);
        this.nodes = new HashMap<>();
        nodes.put(keyOf(root), root);
    }

    /**
     * Integrates a single operation, handling tombstone fallback and reparenting.
     */
    public void integrate(OperationEntry entry) {
        String key = keyOf(entry.getUserID());
        boolean isDelete = "delete".equals(entry.getOperation());

        // If already known
        if (nodes.containsKey(key)) {
            if (isDelete) {
                TreeNode node = nodes.get(key);
                node.setTombstone(true);
                reparentAfterTombstone(node);
            }
            return;
        }
        // Ignore deletes of unknown nodes
        if (isDelete) return;

        // Create new node
        TreeNode node = new TreeNode(entry.getUserID(), entry.getCharacter());

        // Initial parent lookup
        TreeNode parent = root;
        Object[] pid = entry.getParentID();
        if (pid != null) {
            parent = nodes.getOrDefault(keyOf(pid), root);
        }

        // Tombstone fallback: if parent has tombstoned children older than this op,
        // re-parent under the most recent tombstoned child.
        TreeNode fallback = null;
        long ts = node.getTimestamp();
        for (TreeNode child : parent.getChildren()) {
            if (child.isTombstone() && child.getTimestamp() < ts) {
                if (fallback == null || child.getTimestamp() > fallback.getTimestamp()) {
                    fallback = child;
                }
            }
        }
        if (fallback != null) {
            parent = fallback;
        }

        // Attach node
        node.setParent(parent);
        List<TreeNode> siblings = parent.getChildren();
        int pos = findInsertPosition(siblings, ts);
        siblings.add(pos, node);
        nodes.put(key, node);
    }

    /**
     * Integrates a list of operations in any order.
     */
    public void integrateAll(List<OperationEntry> entries) {
        for (OperationEntry e : entries) {
            integrate(e);
        }
    }

    /**
     * After marking a node tombstoned, re-parent its eligible children.
     */
    private void reparentAfterTombstone(TreeNode tomb) {
        TreeNode parent = tomb.getParent();
        List<TreeNode> toMove = new ArrayList<>();
        long ts = tomb.getTimestamp();
        for (TreeNode child : parent.getChildren()) {
            if (child != tomb && child.getTimestamp() > ts) {
                toMove.add(child);
            }
        }
        for (TreeNode child : toMove) {
            parent.getChildren().remove(child);
            child.setParent(tomb);
            List<TreeNode> tombKids = tomb.getChildren();
            int pos = findInsertPosition(tombKids, child.getTimestamp());
            tombKids.add(pos, child);
        }
    }

    /**
     * Pre-order traversal (skipping tombstones).
     */
    public String getSequenceText() {
        StringBuilder sb = new StringBuilder();
        traverse(root, sb);
        return sb.toString();
    }
    private void traverse(TreeNode n, StringBuilder sb) {
        for (TreeNode c : n.getChildren()) {
            if (!c.isTombstone()) sb.append(c.getValue());
            traverse(c, sb);
        }
    }

    /**
     * ASCII print of tree structure.
     */
    public void printTree() {
        printRec(root, "", true);
    }
    private void printRec(TreeNode node, String pref, boolean last) {
        String label = node == root ? "root" : String.valueOf(node.getValue());
        System.out.println(pref + (last ? "└─ " : "├─ ") + label + (node.isTombstone() ? "†" : ""));
        List<TreeNode> kids = node.getChildren();
        for (int i = 0; i < kids.size(); i++) {
            printRec(kids.get(i), pref + (last ? "    " : "│   "), i == kids.size() - 1);
        }
    }

    private int findInsertPosition(List<TreeNode> list, long ts) {
        int i = 0;
        for (; i < list.size(); i++) {
            if (ts > list.get(i).getTimestamp()) break;
        }
        return i;
    }

    private String keyOf(TreeNode n) {
        return Arrays.toString(n.getUserID());
    }
    private String keyOf(Object[] uid) {
        return Arrays.toString(uid);
    }
    public void printAsciiTree() {
        printAsciiRecursive(root, "", true);
    }

    private void printAsciiRecursive(TreeNode node, String prefix, boolean isLast) {
        // label root differently
        String label = (node == root ? "root" : String.valueOf(node.getValue()));
        // choose branch character
        String branch = isLast ? "+-- " : "|-- ";
        // mark tombstones
        String tomb = node.isTombstone() ? "†" : "";
        System.out.println(prefix + branch + label + tomb);

        // prepare next‐level prefix
        String childPrefix = prefix + (isLast ? "    " : "|   ");
        List<TreeNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean lastChild = (i == children.size() - 1);
            printAsciiRecursive(children.get(i), childPrefix, lastChild);
        }
    }
}