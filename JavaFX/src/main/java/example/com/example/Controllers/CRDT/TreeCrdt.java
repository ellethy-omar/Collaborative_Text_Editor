package example.com.example.Controllers.CRDT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TreeCrdt {
    private final TreeNode root;
    private final Map<String, TreeNode> nodes;
    private final Object lock = new Object();

    public TreeCrdt() {
        root = new TreeNode(new Object[]{"root", 0L}, '\0');
        nodes = new HashMap<>();
        nodes.put(root.getIdString(), root);
    }

    public void integrate(OperationEntry entry) {
        synchronized(lock) {
            String idKey = Arrays.toString(entry.getUserID());
            boolean isDelete = "delete".equals(entry.getOperation());

            // If we've seen this operation before
            if (nodes.containsKey(idKey)) {
                if (isDelete) {
                    nodes.get(idKey).setTombstone(true);
                }
                return;
            }
            
            // If delete refers to unknown node, ignore
            if (isDelete) return;

            // Create new node for insert
            TreeNode node = new TreeNode(entry.getUserID(), entry.getCharacter());

            // Find parent node
            Object[] pid = entry.getParentID();
            TreeNode parent = pid == null ? root : nodes.getOrDefault(Arrays.toString(pid), root);

            // Insert in correct position based on both timestamp and userID
            List<TreeNode> siblings = parent.getChildren();
            long timestamp = node.getTimestamp();
            String username = node.getUserID()[0].toString();
            
            int pos = 0;
            for (; pos < siblings.size(); pos++) {
                TreeNode sibling = siblings.get(pos);
                long siblingTs = sibling.getTimestamp();
                String siblingUser = sibling.getUserID()[0].toString();
                
                if (timestamp < siblingTs || 
                    (timestamp == siblingTs && username.compareTo(siblingUser) < 0)) {
                    break;
                }
            }
            
            siblings.add(pos, node);
            nodes.put(idKey, node);
        }
    }

    public void integrateAll(List<OperationEntry> entries) {
        synchronized(lock) {
            // Sort entries by timestamp and then by userID
            entries.sort((a, b) -> {
                long tsA = Long.parseLong(a.getUserID()[1].toString());
                long tsB = Long.parseLong(b.getUserID()[1].toString());
                int timeCompare = Long.compare(tsA, tsB);
                if (timeCompare != 0) return timeCompare;
                
                // If timestamps are equal, sort by userID
                String userA = a.getUserID()[0].toString();
                String userB = b.getUserID()[0].toString();
                return userA.compareTo(userB);
            });
            
            for (OperationEntry entry : entries) {
                integrate(entry);
            }
        }
    }

    public String getSequenceText() {
        synchronized(lock) {
            StringBuilder sb = new StringBuilder();
            List<TreeNode> flattenedNodes = new ArrayList<>();
            flattenTree(root, flattenedNodes);
            
            // Sort flattened nodes by timestamp and userID
            flattenedNodes.sort((a, b) -> {
                long tsA = a.getTimestamp();
                long tsB = b.getTimestamp();
                int timeCompare = Long.compare(tsA, tsB);
                if (timeCompare != 0) return timeCompare;
                
                String userA = a.getUserID()[0].toString();
                String userB = b.getUserID()[0].toString();
                return userA.compareTo(userB);
            });
            
            // Build final text
            for (TreeNode node : flattenedNodes) {
                if (!node.isTombstone() && node != root) {
                    sb.append(node.getValue());
                }
            }
            return sb.toString();
        }
    }

    private void flattenTree(TreeNode node, List<TreeNode> result) {
        if (node == null) return;
        result.add(node);
        for (TreeNode child : node.getChildren()) {
            flattenTree(child, result);
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


