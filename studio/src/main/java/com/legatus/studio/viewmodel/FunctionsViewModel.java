package com.legatus.studio.viewmodel;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.service.BytecodeService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel driving the Functions & Bytecode Workbench.
 */
public class FunctionsViewModel {

    private final ObservableList<BytecodeItem> bytecodeItems = FXCollections.observableArrayList();
    private final ObjectProperty<BytecodeItem> selectedBytecode = new SimpleObjectProperty<>();

    private final StringProperty selectedFilePath = new SimpleStringProperty("");
    private final StringProperty calculatedSha256 = new SimpleStringProperty("");
    private final StringProperty functionNameInput = new SimpleStringProperty("");
    private final StringProperty entrypointInput = new SimpleStringProperty("main");
    private final BooleanProperty isUploading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final BytecodeService bytecodeService;

    public FunctionsViewModel(BytecodeService bytecodeService) {
        this.bytecodeService = Objects.requireNonNull(bytecodeService, "bytecodeService must not be null");
    }

    public void inspectLocalFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        selectedFilePath.set(file.toAbsolutePath().toString());
        String sha256 = BytecodeService.computeFileSha256(file);
        calculatedSha256.set(sha256);

        String fileName = file.getFileName().toString();
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        functionNameInput.set(fileName);
    }

    public CompletableFuture<Boolean> executeUpload() {
        String filePath = selectedFilePath.get();
        if (filePath.isBlank()) {
            statusMessage.set("Please select a valid .slxb bytecode file first.");
            return CompletableFuture.completedFuture(false);
        }

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            statusMessage.set("File does not exist: " + filePath);
            return CompletableFuture.completedFuture(false);
        }

        isUploading.set(true);
        try {
            byte[] bytes = Files.readAllBytes(path);
            String name = functionNameInput.get().isBlank() ? "function" : functionNameInput.get();
            String entry = entrypointInput.get().isBlank() ? "main" : entrypointInput.get();

            return bytecodeService.uploadBytecode(bytes, name)
                    .thenApply(sha -> {
                        com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                            BytecodeItem item = new BytecodeItem(sha, name, entry, bytes.length, java.time.Instant.now().toString());
                            bytecodeItems.add(item);
                            selectedBytecode.set(item);
                            isUploading.set(false);
                            statusMessage.set("Bytecode registered: " + sha.substring(0, 16) + "...");
                        });
                        return true;
                    })
                    .exceptionally(ex -> {
                        com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                            isUploading.set(false);
                            statusMessage.set("Upload failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                        });
                        return false;
                    });
        } catch (IOException e) {
            isUploading.set(false);
            statusMessage.set("Failed to read file: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    public ObservableList<BytecodeItem> getBytecodeItems() { return bytecodeItems; }
    public ObjectProperty<BytecodeItem> selectedBytecodeProperty() { return selectedBytecode; }
    public BytecodeItem getSelectedBytecode() { return selectedBytecode.get(); }
    public void setSelectedBytecode(BytecodeItem item) { this.selectedBytecode.set(item); }

    public StringProperty selectedFilePathProperty() { return selectedFilePath; }
    public StringProperty calculatedSha256Property() { return calculatedSha256; }
    public StringProperty functionNameInputProperty() { return functionNameInput; }
    public StringProperty entrypointInputProperty() { return entrypointInput; }
    public BooleanProperty isUploadingProperty() { return isUploading; }
    public StringProperty statusMessageProperty() { return statusMessage; }
}
