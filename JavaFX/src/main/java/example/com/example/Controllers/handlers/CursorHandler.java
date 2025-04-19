package example.com.example.Controllers.handlers;

import javafx.geometry.Bounds;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws a little vertical line at a given caret position for each remote user.
 */
public class CursorHandler {
    private final TextArea editor;
    private final Pane overlay;
    private final Map<String,Line> cursors = new HashMap<>();

    public CursorHandler(TextArea editor, Pane overlay) {
        this.editor  = editor;
        this.overlay = overlay;
    }

    /**
     * Called whenever we get {username, cursorPos} from the server.
     */
    public void updateCursor(String username, int pos) {
        // compute pixel coords of 'pos' in the TextArea:
        try {
            Bounds bounds = getBoundsForPosition(pos);
            if (bounds == null) return;

            double x = bounds.getMinX();
            double y = bounds.getMinY();

            // create or move the line:
            Line line = cursors.computeIfAbsent(username, u -> {
                Line l = new Line(0,0,0, editor.getFont().getSize());
                l.setStroke(Color.RED);               // you could map users→colors
                l.setStrokeWidth(1);
                overlay.getChildren().add(l);
                return l;
            });
            line.setStartX(x);
            line.setStartY(y);
            line.setEndX(x);
            line.setEndY(y + editor.getFont().getSize());
        } catch (Exception e) {
            // caretBounds might throw if position invalid
        }
    }
    private Bounds getBoundsForPosition(int pos) {
        try {
            Skin<?> skin = editor.getSkin();
            if (skin instanceof TextAreaSkin) {
                TextAreaSkin taSkin = (TextAreaSkin) skin;

                // get internal method: modelToView
                Method method = TextAreaSkin.class.getDeclaredMethod("modelToView", int.class);
                method.setAccessible(true);
                Object result = method.invoke(taSkin, pos);

                if (result instanceof Bounds) {
                    return (Bounds) result;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
