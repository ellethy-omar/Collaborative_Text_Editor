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
        root = new TreeNode(new Object[]{"root", 0L}, '\0');
        nodes = new HashMap<>();
        nodes.put(root.getIdString(), root);
    }

    /**
     * Integrates an OperationEntry into the tree: inserts or tombstones nodes.
     * Does not modify OperationEntry.
     */
    // public void integrate(OperationEntry entry) {
    //     String idKey = Arrays.toString(entry.getUserID());
    //     boolean isDelete = "delete".equals(entry.getOperation());

    //     if (nodes.containsKey(idKey)) {
    //         if (isDelete) {
    //             nodes.get(idKey).setTombstone(true);
    //         }
    //         return;
    //     }
    //     if (isDelete) return;

    //     TreeNode node = new TreeNode(entry.getUserID(), entry.getCharacter());
    //     Object[] pid = entry.getParentID();
    //     TreeNode parent = pid == null ? root : nodes.getOrDefault(Arrays.toString(pid), root);

    //     List<TreeNode> siblings = parent.getChildren();
    //     int pos = findInsertPosition(siblings, node);
    //     siblings.add(pos, node);
    //     nodes.put(idKey, node);
    // }
    public void integrate(OperationEntry entry) {
        String idKey = Arrays.toString(entry.getUserID());
        boolean isDelete = "delete".equals(entry.getOperation());

        // If we've seen this operation before
        if (nodes.containsKey(idKey)) {
            if (isDelete) {
                nodes.get(idKey).setTombstone(true);
            }
            return;
        }
        // If delete refers to unknown, ignore
        if (isDelete) return;

        // Create new node for insert
        TreeNode node = new TreeNode(entry.getUserID(), entry.getCharacter());

        // Determine initial parent
        Object[] pid = entry.getParentID();
        TreeNode parent = (pid == null)
            ? root
            : nodes.getOrDefault(Arrays.toString(pid), root);

        // Re-parent under latest tombstoned child of the initial parent, if any
        long ts = node.getTimestamp();
        TreeNode tombFallback = null;
        for (TreeNode child : parent.getChildren()) {
            if (child.isTombstone() && child.getTimestamp() < ts) {
                if (tombFallback == null || child.getTimestamp() > tombFallback.getTimestamp()) {
                    tombFallback = child;
                }
            }
        }
        if (tombFallback != null) {
            parent = tombFallback;
        }

        // Insert among siblings by descending timestamp (newest first)
        List<TreeNode> siblings = parent.getChildren();
        int pos = 0;
        for (; pos < siblings.size(); pos++) {
            if (ts > siblings.get(pos).getTimestamp()) break;
        }
        siblings.add(pos, node);
        nodes.put(idKey, node);
    }


    public void integrateAll(List<OperationEntry> entries) {
        for (OperationEntry entry : entries) {
            integrate(entry);
        }
    }


    /**
     * Inserts siblings in descending timestamp order (newest first).
     */
    private int findInsertPosition(List<TreeNode> siblings, TreeNode node) {
        long nodeTs = node.getTimestamp();
        for (int i = 0; i < siblings.size(); i++) {
            long sibTs = siblings.get(i).getTimestamp();
            if (nodeTs > sibTs) {
                return i;
            }
        }
        return siblings.size();
    }

    /**
     * Renders the current document by pre-order traversal, skipping tombstones.
     */
    public String getSequenceText() {
        StringBuilder sb = new StringBuilder();
        traverse(root, sb);
        return sb.toString();
    }

    private void traverse(TreeNode node, StringBuilder sb) {
        for (TreeNode child : node.getChildren()) {
            if (!child.isTombstone()) sb.append(child.getValue());
            traverse(child, sb);
        }
    }



    public List<OperationEntry> exportVisibleOperations() {
        List<OperationEntry> ops = new ArrayList<>();
        for (TreeNode child : root.getChildren()) {
            exportVisibleRecursive(child, null, ops);
        }
        return ops;
    }

    private void exportVisibleRecursive(TreeNode node, Object[] parentID, List<OperationEntry> ops) {
        if (!node.isTombstone()) {
            OperationEntry ins = new OperationEntry("insert", node.getValue(), node.getUserID());
            ins.setParentID(parentID);
            ops.add(ins);
        }
        Object[] myID = node.getUserID();
        for (TreeNode child : node.getChildren()) {
            exportVisibleRecursive(child, myID, ops);
        }
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


