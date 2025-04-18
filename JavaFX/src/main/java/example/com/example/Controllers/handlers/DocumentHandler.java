package example.com.example.Controllers.handlers;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.*;

public class DocumentHandler {
    private final TextArea editorArea;
    private String currentFileName;

    public DocumentHandler(TextArea editorArea) {
        this.editorArea = editorArea;
    }

    public void loadFile(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Document");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files","*.txt")
        );
        File file = chooser.showOpenDialog(owner);
        if (file == null) return;
        currentFileName = file.getAbsolutePath();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            Platform.runLater(() -> editorArea.setText(sb.toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveFile(Window owner) {
        if (currentFileName == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Document");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files","*.txt")
            );
            File file = chooser.showSaveDialog(owner);
            if (file == null) return;
            currentFileName = file.getAbsolutePath();
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(currentFileName))) {
            w.write(editorArea.getText());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Clear the current file association. */
    public void clearCurrentFile() {
        currentFileName = null;
    }

    /** Set (or reset) the current file association. */
    public void setCurrentFileName(String fileName) {
        this.currentFileName = fileName;
    }

    /** Get the current file path, or null if none. */
    public String getCurrentFileName() {
        return currentFileName;
    }
}
