package com.legatus.studio.viewmodel;

import com.legatus.studio.transport.LogStreamBuffer;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;

/**
 * ViewModel driving the Bottom Collapsible Drawer (Console logs, Event ticker, Audit inspector).
 */
public class DrawerViewModel {

    private final StringProperty activeTab = new SimpleStringProperty("CONSOLE");
    private final BooleanProperty isAutoScroll = new SimpleBooleanProperty(true);
    private final StringProperty filterText = new SimpleStringProperty("");
    private final ObservableList<String> consoleLines = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    private final LogStreamBuffer logStreamBuffer;

    public DrawerViewModel(LogStreamBuffer logStreamBuffer) {
        this.logStreamBuffer = Objects.requireNonNull(logStreamBuffer, "logStreamBuffer must not be null");

        // Forward stream lines into observable consoleLines list
        this.logStreamBuffer.addLineListener(line -> {
            try {
                javafx.application.Platform.runLater(() -> consoleLines.add(line));
            } catch (IllegalStateException ignored) {
                consoleLines.add(line);
            }
        });
    }

    public void clearConsole() {
        logStreamBuffer.clear();
        consoleLines.clear();
    }

    public void toggleAutoScroll() {
        isAutoScroll.set(!isAutoScroll.get());
    }

    public StringProperty activeTabProperty() { return activeTab; }
    public BooleanProperty isAutoScrollProperty() { return isAutoScroll; }
    public StringProperty filterTextProperty() { return filterText; }
    public ObservableList<String> getConsoleLines() { return consoleLines; }
    public LogStreamBuffer getLogStreamBuffer() { return logStreamBuffer; }
}
