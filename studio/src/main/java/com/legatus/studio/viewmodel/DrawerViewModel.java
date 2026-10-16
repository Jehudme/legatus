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
    private final javafx.collections.transformation.FilteredList<String> filteredConsoleLines;

    private final LogStreamBuffer logStreamBuffer;

    public DrawerViewModel(LogStreamBuffer logStreamBuffer) {
        this.logStreamBuffer = Objects.requireNonNull(logStreamBuffer, "logStreamBuffer must not be null");
        this.filteredConsoleLines = new javafx.collections.transformation.FilteredList<>(consoleLines, this::matchesFilter);
        this.filterText.addListener((obs, oldV, newV) -> filteredConsoleLines.setPredicate(this::matchesFilter));

        // Forward stream lines onto JavaFX Application Thread safely with 10,000-line ceiling
        this.logStreamBuffer.addLineListener(line -> {
            com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                consoleLines.add(line);
                if (consoleLines.size() > 10_000) {
                    int excess = consoleLines.size() - 10_000;
                    consoleLines.remove(0, excess);
                }
            });
        });
    }

    private boolean matchesFilter(String line) {
        if (line == null) return false;
        String filter = filterText.get();
        if (filter == null || filter.isBlank()) return true;
        return line.toLowerCase().contains(filter.toLowerCase().trim());
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
    public javafx.collections.transformation.FilteredList<String> getFilteredConsoleLines() { return filteredConsoleLines; }
    public LogStreamBuffer getLogStreamBuffer() { return logStreamBuffer; }
}
