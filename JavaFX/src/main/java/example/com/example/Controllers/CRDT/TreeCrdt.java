package example.com.example.Controllers.CRDT;

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
    public void integrate(OperationEntry entry) {
        String idKey = Arrays.toString(entry.getUserID());
        boolean isDelete = "delete".equals(entry.getOperation());

        if (nodes.containsKey(idKey)) {
            if (isDelete) {
                nodes.get(idKey).setTombstone(true);
            }
            return;
        }
        if (isDelete) return;

        TreeNode node = new TreeNode(entry.getUserID(), entry.getCharacter());
        Object[] pid = entry.getParentID();
        TreeNode parent = pid == null ? root : nodes.getOrDefault(Arrays.toString(pid), root);

        List<TreeNode> siblings = parent.getChildren();
        int pos = findInsertPosition(siblings, node);
        siblings.add(pos, node);
        nodes.put(idKey, node);
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
}



// public class TreeCrdt {
//     private final TreeNode root;
//     private final Map<String,TreeNode> nodes;

//     public TreeCrdt() {
//         this.root = new TreeNode(new Object[]{"root", 0L}, '\0');
//         this.nodes = new HashMap<>();
//         nodes.put(root.getIdString(), root);
//     }

//     /**
//      * Integrate your app’s OperationEntry directly.
//      */
//     public void integrate(OperationEntry entry) {
//         String idKey = Arrays.toString(entry.getUserID());
//         boolean isDelete = "delete".equals(entry.getOperation());

//         // if we’ve already seen it…
//         if (nodes.containsKey(idKey)) {
//             if (isDelete) {
//                 // mark the existing node as a tombstone
//                 nodes.get(idKey).setTombstone(true);
//             }
//             return;
//         }

//         // if it’s a delete for a missing node, just ignore
//         if (isDelete) return;

//         // otherwise, it’s an insert → build a new TreeNode
//         TreeNode node = new TreeNode(entry.getUserID(), entry.getCharacter());

//         // find the parent (null ⇒ root)
//         Object[] pid = entry.getParentID();
//         TreeNode parent = pid == null
//             ? root
//             : nodes.getOrDefault(Arrays.toString(pid), root);

//         // determine the sibling index (by userID then timestamp)
//         List<TreeNode> siblings = parent.getChildren();
//         int pos = findInsertPosition(siblings, node);
//         siblings.add(pos, node);

//         // remember it
//         nodes.put(idKey, node);
//     }
//     private int getUserPriority(TreeNode n) {
//         String uid = n.getUserID()[0].toString();
//         Matcher m = Pattern.compile("(\\d+)$").matcher(uid);
//         if (m.find()) {
//             return Integer.parseInt(m.group(1));
//         } else {
//             return Integer.MAX_VALUE;  // no digits => lowest priority
//         }
//     }

//     private int findInsertPosition(List<TreeNode> siblings, TreeNode node) {
//         int nodePrio = getUserPriority(node);
//         long nodeTs = node.getTimestamp();

//         for (int i = 0; i < siblings.size(); i++) {
//             TreeNode sib = siblings.get(i);
//             int sibPrio = getUserPriority(sib);
//             long sibTs   = sib.getTimestamp();

//             // first by numeric user priority, then by timestamp
//             if (nodePrio < sibPrio ||
//                (nodePrio == sibPrio && nodeTs < sibTs)) {
//                 return i;
//             }
//         }
//         return siblings.size();
//     }

//     public String getSequenceText() {
//         StringBuilder sb = new StringBuilder();
//         traverse(root, sb);
//         return sb.toString();
//     }
//     private void traverse(TreeNode n, StringBuilder sb) {
//         for (TreeNode c : n.getChildren()) {
//             if (!c.isTombstone()) sb.append(c.getValue());
//             traverse(c, sb);
//         }
//     }
// }