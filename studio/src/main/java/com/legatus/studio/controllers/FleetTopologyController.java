package com.legatus.studio.controllers;

import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.viewmodel.FleetViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class FleetTopologyController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> stateFilterCombo;
    @FXML private TableView<WorkerNode> workersTable;
    @FXML private TableColumn<WorkerNode, String> workerIdCol;
    @FXML private TableColumn<WorkerNode, String> hostnameCol;
    @FXML private TableColumn<WorkerNode, String> stateCol;
    @FXML private TableColumn<WorkerNode, Number> cpuCol;

    @FXML private Label selectedWorkerIdLabel;
    @FXML private Label selectedHostLabel;
    @FXML private Label selectedStateLabel;
    @FXML private Label selectedCpuLabel;
    @FXML private Button drainButton;
    @FXML private Button revokeButton;

    private FleetViewModel viewModel;

    public void setViewModel(FleetViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    @FXML
    public void initialize() {
        if (viewModel != null) {
            bindViewModel();
        }
    }

    private void bindViewModel() {
        if (viewModel == null) return;

        searchField.textProperty().bindBidirectional(viewModel.filterQueryProperty());
        workersTable.setItems(viewModel.getFilteredWorkers());

        workerIdCol.setCellValueFactory(c -> c.getValue().workerIdProperty());
        hostnameCol.setCellValueFactory(c -> c.getValue().hostnameProperty());
        stateCol.setCellValueFactory(c -> c.getValue().stateProperty());
        cpuCol.setCellValueFactory(c -> c.getValue().cpuLoadPctProperty());

        workersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                viewModel.setSelectedWorker(newV);
                selectedWorkerIdLabel.setText(newV.getWorkerId());
                selectedHostLabel.setText(newV.getHostname());
                selectedStateLabel.setText(newV.getState());
                selectedCpuLabel.setText(String.format("%.1f%%", newV.getCpuLoadPct()));
            }
        });
    }

    @FXML
    private void onDrainClicked() {
        if (viewModel != null) viewModel.drainSelectedWorker();
    }

    @FXML
    private void onRevokeClicked() {
        if (viewModel != null) viewModel.revokeSelectedWorker();
    }
}
