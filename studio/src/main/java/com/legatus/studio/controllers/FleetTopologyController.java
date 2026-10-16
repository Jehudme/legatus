package com.legatus.studio.controllers;

import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.viewmodel.FleetViewModel;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class FleetTopologyController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> stateFilterCombo;
    @FXML private TableView<WorkerNode> workersTable;

    @FXML private TableColumn<WorkerNode, String> nameCol;
    @FXML private TableColumn<WorkerNode, Number> coresCol;
    @FXML private TableColumn<WorkerNode, String> workerIdCol;
    @FXML private TableColumn<WorkerNode, String> hostnameCol;
    @FXML private TableColumn<WorkerNode, String> ipCol;
    @FXML private TableColumn<WorkerNode, String> uptimeCol;
    @FXML private TableColumn<WorkerNode, Number> cpuCol;
    @FXML private TableColumn<WorkerNode, String> ramCol;
    @FXML private TableColumn<WorkerNode, Number> tasksCol;
    @FXML private TableColumn<WorkerNode, String> leaseCol;
    @FXML private TableColumn<WorkerNode, String> stateCol;
    @FXML private TableColumn<WorkerNode, Boolean> activeCol;

    // Inspector pane & controls
    @FXML private VBox inspectorPane;
    @FXML private TextField nameField;
    @FXML private Button saveNameButton;
    @FXML private Label selectedWorkerIdLabel;
    @FXML private Label selectedHostLabel;
    @FXML private Label selectedIpOsLabel;
    @FXML private Label selectedStateLabel;
    @FXML private CheckBox activeCheckBox;
    @FXML private CheckBox autoRenewCheckBox;
    @FXML private Label selectedLeaseLabel;

    @FXML private Slider targetCpuSlider;
    @FXML private Label targetCpuValueLabel;
    @FXML private Slider targetRamSlider;
    @FXML private Label targetRamValueLabel;
    @FXML private Spinner<Integer> maxTasksSpinner;
    @FXML private Button saveThresholdsButton;

    @FXML private Label selectedCpuLabel;
    @FXML private Label selectedRamLabel;
    @FXML private Label selectedTasksLabel;

    @FXML private Button drainButton;
    @FXML private Button revokeButton;
    @FXML private Button deleteButton;

    private FleetViewModel viewModel;

    public void setViewModel(FleetViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    @FXML
    public void initialize() {
        if (stateFilterCombo != null) {
            stateFilterCombo.getItems().setAll("ALL", "ONLINE", "INACTIVE", "DRAINING", "OFFLINE");
            stateFilterCombo.setValue("ALL");
        }
        if (maxTasksSpinner != null) {
            maxTasksSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 64, 16));
        }
        if (targetCpuSlider != null && targetCpuValueLabel != null) {
            targetCpuSlider.valueProperty().addListener((obs, oldV, newV) -> {
                targetCpuValueLabel.setText(String.format("%.1f%%", newV.doubleValue()));
            });
        }
        if (targetRamSlider != null && targetRamValueLabel != null) {
            targetRamSlider.valueProperty().addListener((obs, oldV, newV) -> {
                targetRamValueLabel.setText(String.format("%.1f%%", newV.doubleValue()));
            });
        }
        if (viewModel != null) {
            bindViewModel();
        }
    }

    private void bindViewModel() {
        if (viewModel == null) return;

        if (searchField != null) {
            searchField.textProperty().bindBidirectional(viewModel.filterQueryProperty());
        }
        if (stateFilterCombo != null) {
            stateFilterCombo.valueProperty().bindBidirectional(viewModel.stateFilterProperty());
        }

        viewModel.getSortedWorkers().comparatorProperty().bind(workersTable.comparatorProperty());
        workersTable.setItems(viewModel.getSortedWorkers());

        // Cell value factories
        if (nameCol != null) {
            nameCol.setCellValueFactory(c -> Bindings.createStringBinding(
                    () -> c.getValue().getDisplayName(),
                    c.getValue().friendlyNameProperty(), c.getValue().hostnameProperty(), c.getValue().workerIdProperty()
            ));
        }
        if (coresCol != null) {
            coresCol.setCellValueFactory(c -> c.getValue().allocatableCoresProperty());
        }
        if (workerIdCol != null) {
            workerIdCol.setCellValueFactory(c -> c.getValue().workerIdProperty());
        }
        if (hostnameCol != null) {
            hostnameCol.setCellValueFactory(c -> c.getValue().hostnameProperty());
        }
        if (ipCol != null) {
            ipCol.setCellValueFactory(c -> c.getValue().sourceIpProperty());
        }
        if (uptimeCol != null) {
            uptimeCol.setCellValueFactory(c -> Bindings.createStringBinding(
                    () -> c.getValue().getFormattedUptime(),
                    c.getValue().uptimeSecondsProperty()
            ));
        }
        if (cpuCol != null) {
            cpuCol.setCellValueFactory(c -> c.getValue().cpuLoadPctProperty());
            cpuCol.setCellFactory(col -> new TableCell<>() {
                private final ProgressBar bar = new ProgressBar(0);
                private final Label label = new Label();
                private final HBox box = new HBox(6, bar, label);
                {
                    bar.setPrefWidth(55);
                    bar.setStyle("-fx-accent: #38bdf8;");
                    box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                }
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        double pct = item.doubleValue();
                        bar.setProgress(Math.max(0.0, Math.min(1.0, pct / 100.0)));
                        label.setText(String.format("%.1f%%", pct));
                        setGraphic(box);
                    }
                }
            });
        }
        if (ramCol != null) {
            ramCol.setCellValueFactory(c -> Bindings.createStringBinding(() -> {
                double usedGb = c.getValue().getMemoryUsedBytes() / (1024.0 * 1024.0 * 1024.0);
                double maxGb = c.getValue().getMaxMemoryBytes() / (1024.0 * 1024.0 * 1024.0);
                return String.format("%.1f / %.1f GB", usedGb, maxGb);
            }, c.getValue().memoryUsedBytesProperty(), c.getValue().maxMemoryBytesProperty()));
        }
        if (tasksCol != null) {
            tasksCol.setCellValueFactory(c -> c.getValue().activeTasksCountProperty());
        }
        if (leaseCol != null) {
            leaseCol.setCellValueFactory(c -> Bindings.createStringBinding(
                    () -> c.getValue().getFormattedLeaseRemaining() + (c.getValue().isAutoRenewLease() ? " [R]" : ""),
                    c.getValue().remainingLeaseSecProperty(), c.getValue().autoRenewLeaseProperty()
            ));
            leaseCol.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        WorkerNode w = getTableRow() != null ? getTableRow().getItem() : null;
                        long remaining = w != null ? w.getRemainingLeaseSec() : 0L;
                        Label badge = new Label(item);
                        if (remaining > 600) {
                            badge.getStyleClass().setAll("badge-lease-green");
                        } else if (remaining > 120) {
                            badge.getStyleClass().setAll("badge-lease-amber");
                        } else {
                            badge.getStyleClass().setAll("badge-lease-red");
                        }
                        setGraphic(badge);
                    }
                }
            });
        }
        if (stateCol != null) {
            stateCol.setCellValueFactory(c -> c.getValue().stateProperty());
            stateCol.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String state, boolean empty) {
                    super.updateItem(state, empty);
                    if (empty || state == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        Label badge = new Label(state);
                        if ("ONLINE".equalsIgnoreCase(state)) {
                            badge.getStyleClass().setAll("badge-online");
                        } else if ("INACTIVE".equalsIgnoreCase(state)) {
                            badge.getStyleClass().setAll("badge-inactive");
                        } else if ("DRAINING".equalsIgnoreCase(state)) {
                            badge.getStyleClass().setAll("badge-draining");
                        } else {
                            badge.getStyleClass().setAll("badge-offline");
                        }
                        setGraphic(badge);
                    }
                }
            });
        }
        if (activeCol != null) {
            activeCol.setCellValueFactory(c -> c.getValue().activeProperty());
            activeCol.setCellFactory(col -> new TableCell<>() {
                private final CheckBox cb = new CheckBox();
                {
                    cb.setOnAction(e -> {
                        if (getTableRow() != null && getTableRow().getItem() != null) {
                            viewModel.setSelectedWorker(getTableRow().getItem());
                            viewModel.toggleActiveSelectedWorker();
                        }
                    });
                }
                @Override
                protected void updateItem(Boolean active, boolean empty) {
                    super.updateItem(active, empty);
                    if (empty || active == null) {
                        setGraphic(null);
                    } else {
                        cb.setSelected(active);
                        cb.setText(active ? "Active" : "Paused");
                        setGraphic(cb);
                    }
                }
            });
        }

        // Inspector visibility bound to table selection
        if (inspectorPane != null) {
            inspectorPane.visibleProperty().bind(workersTable.getSelectionModel().selectedItemProperty().isNotNull());
            inspectorPane.managedProperty().bind(inspectorPane.visibleProperty());
        }

        // Selection listener to populate inspector dynamically
        workersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            unbindInspector();
            if (newV != null) {
                viewModel.setSelectedWorker(newV);
                bindInspector(newV);
            } else {
                viewModel.setSelectedWorker(null);
            }
        });

        // Error alert listener
        if (viewModel.errorMessageProperty() != null) {
            viewModel.errorMessageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    showErrorAlert("Fleet Action Failure", newVal);
                }
            });
        }
    }

    private final java.util.List<Runnable> inspectorUnbinders = new java.util.ArrayList<>();

    public void unbindInspector() {
        for (Runnable unbinder : inspectorUnbinders) {
            try { unbinder.run(); } catch (Exception ignored) {}
        }
        inspectorUnbinders.clear();

        if (selectedCpuLabel != null) selectedCpuLabel.textProperty().unbind();
        if (selectedRamLabel != null) selectedRamLabel.textProperty().unbind();
        if (selectedLeaseLabel != null) selectedLeaseLabel.textProperty().unbind();
        if (selectedStateLabel != null) selectedStateLabel.textProperty().unbind();
        if (selectedTasksLabel != null) selectedTasksLabel.textProperty().unbind();
        if (selectedWorkerIdLabel != null) selectedWorkerIdLabel.textProperty().unbind();
        if (selectedHostLabel != null) selectedHostLabel.textProperty().unbind();
    }

    public void bindInspector(WorkerNode newV) {
        if (nameField != null) nameField.setText(newV.getFriendlyName());
        if (selectedWorkerIdLabel != null) selectedWorkerIdLabel.setText(newV.getWorkerId());
        if (selectedHostLabel != null) selectedHostLabel.setText(newV.getHostname());
        if (selectedIpOsLabel != null) selectedIpOsLabel.setText(newV.getSourceIp() + " | " + newV.getOs() + "/" + newV.getArchitecture());
        if (activeCheckBox != null) activeCheckBox.setSelected(newV.isActive());
        if (autoRenewCheckBox != null) autoRenewCheckBox.setSelected(newV.isAutoRenewLease());
        if (targetCpuSlider != null) targetCpuSlider.setValue(newV.getTargetCpuPct());
        if (targetRamSlider != null) targetRamSlider.setValue(newV.getTargetRamPct());
        if (maxTasksSpinner != null && maxTasksSpinner.getValueFactory() != null) {
            maxTasksSpinner.getValueFactory().setValue(newV.getMaxConcurrentTasks());
        }

        if (selectedStateLabel != null) {
            selectedStateLabel.textProperty().bind(newV.stateProperty());
            var stateListener = (javafx.beans.value.ChangeListener<String>) (obs, oldS, newS) -> updateInspectorStateBadge(newS);
            newV.stateProperty().addListener(stateListener);
            inspectorUnbinders.add(() -> newV.stateProperty().removeListener(stateListener));
            updateInspectorStateBadge(newV.getState());
        }

        if (selectedCpuLabel != null) {
            selectedCpuLabel.textProperty().bind(Bindings.createStringBinding(
                    () -> String.format("%.1f%%", newV.getCpuLoadPct()),
                    newV.cpuLoadPctProperty()
            ));
        }

        if (selectedRamLabel != null) {
            selectedRamLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                double usedGb = newV.getMemoryUsedBytes() / (1024.0 * 1024.0 * 1024.0);
                double maxGb = newV.getMaxMemoryBytes() / (1024.0 * 1024.0 * 1024.0);
                return String.format("%.1f / %.1f GB", usedGb, maxGb);
            }, newV.memoryUsedBytesProperty(), newV.maxMemoryBytesProperty()));
        }

        if (selectedLeaseLabel != null) {
            selectedLeaseLabel.textProperty().bind(Bindings.createStringBinding(
                    newV::getFormattedLeaseRemaining,
                    newV.remainingLeaseSecProperty()
            ));
        }

        if (selectedTasksLabel != null) {
            selectedTasksLabel.textProperty().bind(Bindings.createStringBinding(
                    () -> String.valueOf(newV.getActiveTasksCount()),
                    newV.activeTasksCountProperty()
            ));
        }
    }

    private void updateInspectorStateBadge(String state) {
        if (selectedStateLabel == null || state == null) return;
        selectedStateLabel.getStyleClass().removeAll("badge-online", "badge-inactive", "badge-draining", "badge-offline");
        if ("ONLINE".equalsIgnoreCase(state)) {
            selectedStateLabel.getStyleClass().add("badge-online");
        } else if ("INACTIVE".equalsIgnoreCase(state)) {
            selectedStateLabel.getStyleClass().add("badge-inactive");
        } else if ("DRAINING".equalsIgnoreCase(state)) {
            selectedStateLabel.getStyleClass().add("badge-draining");
        } else {
            selectedStateLabel.getStyleClass().add("badge-offline");
        }
    }

    private void showErrorAlert(String title, String message) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        } catch (Exception e) {
            System.err.println("[ALERT ERROR] " + title + ": " + message);
        }
    }

    @FXML
    public void onSaveNameClicked() {
        if (viewModel != null && nameField != null) {
            viewModel.renameSelectedWorker(nameField.getText());
        }
    }

    @FXML
    public void onActiveToggled() {
        if (viewModel != null) {
            viewModel.toggleActiveSelectedWorker();
        }
    }

    @FXML
    public void onAutoRenewToggled() {
        if (viewModel != null && autoRenewCheckBox != null) {
            viewModel.toggleAutoRenewSelectedWorker(autoRenewCheckBox.isSelected());
        }
    }

    @FXML
    public void onSaveThresholdsClicked() {
        if (viewModel != null && targetCpuSlider != null && targetRamSlider != null && maxTasksSpinner != null) {
            viewModel.updateThresholdsSelectedWorker(
                    targetCpuSlider.getValue(),
                    targetRamSlider.getValue(),
                    maxTasksSpinner.getValue()
            );
        }
    }

    @FXML
    public void onDrainClicked() {
        if (viewModel != null) viewModel.drainSelectedWorker();
    }

    @FXML
    public void onRevokeClicked() {
        if (viewModel != null) viewModel.revokeSelectedWorker();
    }

    @FXML
    public void onDeleteClicked() {
        if (viewModel != null) {
            WorkerNode selected = viewModel.getSelectedWorker();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Revoke Lease & Delete Worker");
                confirm.setHeaderText("Decommission Worker: " + selected.getDisplayName());
                confirm.setContentText("Are you sure you want to revoke lease and delete worker '" + selected.getDisplayName()
                        + "' (" + selected.getWorkerId() + ")? In-flight tasks will be gracefully terminated and the node purged from the fleet.");
                var result = confirm.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    viewModel.deleteSelectedWorker();
                }
            }
        }
    }

    public TableView<WorkerNode> getWorkersTable() {
        return workersTable;
    }

    public VBox getInspectorPane() {
        return inspectorPane;
    }

    public TextField getNameField() {
        return nameField;
    }

    public CheckBox getActiveCheckBox() {
        return activeCheckBox;
    }

    public CheckBox getAutoRenewCheckBox() {
        return autoRenewCheckBox;
    }

    public Slider getTargetCpuSlider() {
        return targetCpuSlider;
    }

    public Slider getTargetRamSlider() {
        return targetRamSlider;
    }

    public Spinner<Integer> getMaxTasksSpinner() {
        return maxTasksSpinner;
    }

    public Label getSelectedCpuLabel() { return selectedCpuLabel; }
    public Label getSelectedRamLabel() { return selectedRamLabel; }
    public Label getSelectedLeaseLabel() { return selectedLeaseLabel; }
    public Label getSelectedStateLabel() { return selectedStateLabel; }
    public Label getSelectedTasksLabel() { return selectedTasksLabel; }
}
