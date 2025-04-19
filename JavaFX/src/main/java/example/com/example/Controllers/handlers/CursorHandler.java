package example.com.example.Controllers.handlers;

import javafx.geometry.Bounds;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Draws a little vertical line at a given caret position for each remote user.
 */
public class CursorHandler {
    private final TextArea editor;
    private final Pane overlay;
    private final Map<String, Line> cursors = new HashMap<>();
    private final Map<String, Color> userColors = new HashMap<>();
    private final Deque<String> availableNames;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CursorHandler(TextArea editor, Pane overlay) {
        this.editor = editor;
        this.overlay = overlay;

        // Initialize available names and map them to colors
        availableNames = new ArrayDeque<>(Arrays.asList("user1", "user2", "user3", "user4"));
        assignColorsToUsers();
    }

    // Map users to specific colors, you can change colors here or expand if more users
    private void assignColorsToUsers() {
        List<Color> colors = Arrays.asList(
                Color.RED, Color.GREEN, Color.BLUE, Color.ORANGE
        );
        Iterator<Color> colorIterator = colors.iterator();

        // Assign colors to users
        for (String username : availableNames) {
            if (colorIterator.hasNext()) {
                userColors.put(username, colorIterator.next());
            }
        }
    }

    /**
     * Called whenever we get {username, cursorPos} from the server.
     */
    public void updateCursor(String username, int pos) {
        // Ensure thread-safety when accessing or modifying cursors map
        lock.writeLock().lock();
        try {
            // compute pixel coords of 'pos' in the TextArea:
            Bounds bounds = getBoundsForPosition(pos);
            System.out.println(bounds);
            if (bounds == null) return;

            double x = bounds.getMinX();
            double y = bounds.getMinY();

            // Create or move the line:
            Line line = cursors.computeIfAbsent(username, u -> {
                Line l = new Line(0, 0, 0, editor.getFont().getSize());
                Color userColor = userColors.getOrDefault(username, Color.BLACK); // Default to black if no color found
                l.setStroke(userColor);
                l.setStrokeWidth(1);
                overlay.getChildren().add(l);
                return l;
            });

            line.setStartX(x);
            line.setStartY(y);
            line.setEndX(x);
            line.setEndY(y + editor.getFont().getSize());

        } catch (Exception e) {
            // Log exception but don't crash the program
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Bounds getBoundsForPosition(int pos) {
        try {
            Skin<?> skin = editor.getSkin();
            if (skin instanceof TextAreaSkin) {
                TextAreaSkin taSkin = (TextAreaSkin) skin;

                // Access internal method modelToView to get bounds of position in TextArea
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