package example.com.example.Controllers.utils;

import javafx.geometry.Bounds;
import javafx.scene.control.TextArea;
import javafx.scene.Node;

public class CursorUtils {

    public static Bounds getCaretBounds(TextArea area) {
        Node caretNode = area.lookup(".caret");
        if (caretNode != null) {
            return caretNode.localToScene(caretNode.getBoundsInLocal());
        }
        return null;
    }
}
