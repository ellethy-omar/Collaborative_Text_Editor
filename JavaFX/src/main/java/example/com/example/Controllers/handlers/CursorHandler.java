package example.com.example.Controllers.handlers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Robust cursor overlay for JavaFX TextArea:
 *  • Computes caret x/y by measuring the prefix string
 *  • Adjusts for insets & scroll offsets
 *  • Re‑positions on text/change/scroll
 *  • Flashes & labels each user’s caret
 */
public class CursorHandler {
    private final TextArea editor;
    private final Pane overlay;

    // map username → representation
    private final Map<String, CursorRepresentation> cursors = new HashMap<>();
    // assign each user one of up to 4 contrasting colors
    private final Map<String, Color> userColors = new HashMap<>();

    // helper Text node to measure string widths
    private final Text measurer = new Text();
    // approximate single‑line height (will be reassigned once scene is ready)
    private double lineHeight;

    // your 4 chosen colors:
    private static final Color[] CURSOR_COLORS = {
            Color.rgb(255,   0,   0, 0.8), // red
            Color.rgb(  0, 128, 255, 0.8), // blue
            Color.rgb(  0, 180,   0, 0.8), // green
            Color.rgb(255, 140,   0, 0.8)  // orange
    };

    public CursorHandler(TextArea editor, Pane overlay) {
        this.editor  = editor;
        this.overlay = overlay;

        // bind overlay exactly to the TextArea’s size
        overlay.prefWidthProperty().bind(editor.widthProperty());
        overlay.prefHeightProperty().bind(editor.heightProperty());
        overlay.setMouseTransparent(true);

        // configure our measurer
        measurer.setFont(editor.getFont());

        // calculate an initial lineHeight; once the editor is in the scene this will stabilize
        Platform.runLater(() -> {
            measurer.applyCss();
            lineHeight = measurer.getLayoutBounds().getHeight() * 1.2;
        });

        // whenever text or scroll changes, recompute all cursors
        editor.textProperty().addListener((obs, o, n) -> refreshAll());
        editor.scrollTopProperty().addListener((obs, o, n) -> refreshAll());
        editor.scrollLeftProperty().addListener((obs, o, n) -> refreshAll());
    }

    /**
     * Called by your STOMP handler on receipt of a remote cursor update.
     */
    public void updateCursor(String username, int position) {
        Platform.runLater(() -> {
            // assign a color if needed
            userColors.computeIfAbsent(username, u ->
                    CURSOR_COLORS[userColors.size() % CURSOR_COLORS.length]
            );
            Color c = userColors.get(username);

            // create or fetch the representation
            CursorRepresentation rep = cursors.computeIfAbsent(username,
                    u -> new CursorRepresentation(u, c, overlay));

            // store the raw text‑index
            rep.setPosition(position);
            // position it now
            positionCursor(rep);
        });
    }

    /** redraw every cursor (e.g. on scroll or text change) */
    private void refreshAll() {
        for (CursorRepresentation rep : cursors.values()) {
            positionCursor(rep);
        }
    }

    /** compute screen x/y for caret‑offset, then update the nodes */
    private void positionCursor(CursorRepresentation rep) {
        int pos = rep.getPosition();
        String text = editor.getText();

        // clamp into [0..length]
        pos = Math.max(0, Math.min(pos, text.length()));

        // split into lines to compute row & prefix
        String before = text.substring(0, pos);
        int row = (int) before.chars().filter(ch -> ch == '\n').count();
        int lastNL = before.lastIndexOf('\n');
        String prefix = lastNL >= 0
                ? before.substring(lastNL + 1)
                : before;

        // measure the prefix width
        measurer.setText(prefix.isEmpty() ? " " : prefix);
        measurer.applyCss();
        double prefixW = measurer.getLayoutBounds().getWidth();

        // gather Insets & scroll position
        double insetX   = editor.getInsets().getLeft();
        double insetY   = editor.getInsets().getTop();
        double scrollX  = editor.getScrollLeft();
        double scrollY  = editor.getScrollTop();

        // compute final coords
        double x = insetX + prefixW - scrollX;
        double y = insetY + row * lineHeight  - scrollY;

        // update the rep
        rep.updateNodePositions(x, y, lineHeight);
    }

    /** clear out one user’s cursor (e.g. on disconnect) */
    public void removeCursor(String username) {
        Platform.runLater(() -> {
            CursorRepresentation rep = cursors.remove(username);
            if (rep != null) rep.remove();
            userColors.remove(username);
        });
    }

    // —————————————————————————————————————————————————————————————
    // inner class that holds the Line, the label, and the flash animation
    private static class CursorRepresentation {
        private final Line cursorLine;
        private final Text nameLabel;
        private final Timeline flash;
        private int position;

        CursorRepresentation(String user, Color color, Pane parent) {
            // line
            cursorLine = new Line();
            cursorLine.setStroke(color);
            cursorLine.setStrokeWidth(2);

            // label
            nameLabel = new Text(user);
            nameLabel.setFill(color);
            nameLabel.setStyle("-fx-font-size:10px;");

            // add to overlay
            parent.getChildren().addAll(cursorLine, nameLabel);

            // flashing animation
            flash = new Timeline(
                    new KeyFrame(Duration.ZERO,      e -> cursorLine.setVisible(true)),
                    new KeyFrame(Duration.seconds(0.5), e -> cursorLine.setVisible(false)),
                    new KeyFrame(Duration.seconds(1))
            );
            flash.setCycleCount(Timeline.INDEFINITE);
            flash.play();
        }

        void setPosition(int pos) {
            this.position = pos;
        }
        int getPosition() {
            return position;
        }

        /** move the visuals to x,y */
        void updateNodePositions(double x, double y, double height) {
            cursorLine.setStartX(x);
            cursorLine.setStartY(y);
            cursorLine.setEndX(x);
            cursorLine.setEndY(y + height);

            nameLabel.setX(x + 3);
            nameLabel.setY(y - 3);
        }

        /** tear down */
        void remove() {
            flash.stop();
            ((Pane)cursorLine.getParent())
                    .getChildren().removeAll(cursorLine, nameLabel);
        }
    }
}
