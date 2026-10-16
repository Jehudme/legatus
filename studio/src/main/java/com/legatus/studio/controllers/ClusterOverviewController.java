package com.legatus.studio.controllers;

import com.legatus.studio.viewmodel.ClusterOverviewViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class ClusterOverviewController {

    @FXML private Label onlineWorkersCountLabel;
    @FXML private Label cpuLoadLabel;
    @FXML private Label memoryUsedLabel;
    @FXML private Label activeTasksLabel;
    @FXML private javafx.scene.layout.HBox thresholdBreachBox;
    @FXML private Label thresholdBreachLabel;
    @FXML private javafx.scene.layout.HBox queueSaturatedBox;
    @FXML private Label queueSaturatedLabel;
    @FXML private Label workerCountBadge;
    @FXML private javafx.scene.layout.FlowPane workerCardsPane;

    private final java.util.Map<String, javafx.scene.Node> cardMap = new java.util.HashMap<>();

    private ClusterOverviewViewModel viewModel;

    public void setViewModel(ClusterOverviewViewModel viewModel) {
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
        var metrics = viewModel.getClusterMetrics();
        onlineWorkersCountLabel.textProperty().bind(metrics.onlineWorkersCountProperty().asString());
        cpuLoadLabel.textProperty().bind(metrics.clusterCpuPercentProperty().asString("%.1f%%"));
        activeTasksLabel.textProperty().bind(metrics.executingTasksCountProperty().asString());
        if (memoryUsedLabel != null) {
            var memBinding = javafx.beans.binding.Bindings.createStringBinding(() -> {
                double usedGb = metrics.getClusterUsedMemoryBytes() / (1024.0 * 1024.0 * 1024.0);
                double totalGb = metrics.getClusterTotalMemoryBytes() / (1024.0 * 1024.0 * 1024.0);
                return String.format("%.1f / %.1f GB", usedGb, totalGb);
            }, metrics.clusterUsedMemoryBytesProperty(), metrics.clusterTotalMemoryBytesProperty());
            memoryUsedLabel.textProperty().bind(memBinding);
        }

        // 1. Auto-Balancer Alert Banner
        if (thresholdBreachBox != null) {
            thresholdBreachBox.visibleProperty().bind(viewModel.thresholdBreachWarningProperty());
            thresholdBreachBox.managedProperty().bind(thresholdBreachBox.visibleProperty());

            viewModel.getBreachedWorkerIds().addListener((javafx.collections.ListChangeListener<String>) c -> {
                updateBreachText();
            });
            updateBreachText();
        }

        // 2. Queue Saturation Alert Banner
        if (queueSaturatedBox != null) {
            queueSaturatedBox.visibleProperty().bind(viewModel.queueSaturatedErrorProperty());
            queueSaturatedBox.managedProperty().bind(queueSaturatedBox.visibleProperty());
        }

        // 3. Worker Cards Grid
        if (workerCountBadge != null) {
            var countBinding = javafx.beans.binding.Bindings.createStringBinding(
                    () -> viewModel.getWorkers().size() + " nodes",
                    viewModel.getWorkers()
            );
            workerCountBadge.textProperty().bind(countBinding);
        }

        if (workerCardsPane != null) {
            rebuildWorkerCards();
            viewModel.getWorkers().addListener((javafx.collections.ListChangeListener<com.legatus.studio.model.WorkerNode>) c -> {
                while (c.next()) {
                    if (c.wasAdded()) {
                        for (var worker : c.getAddedSubList()) {
                            addWorkerCard(worker);
                        }
                    }
                    if (c.wasRemoved()) {
                        for (var worker : c.getRemoved()) {
                            removeWorkerCard(worker);
                        }
                    }
                }
            });
        }
    }

    private void updateBreachText() {
        if (thresholdBreachLabel == null || viewModel == null) return;
        var breached = viewModel.getBreachedWorkerIds();
        if (breached.isEmpty()) {
            thresholdBreachLabel.setText("Auto-Balancer Alert: Worker thresholds breached to satisfy task demand.");
        } else {
            thresholdBreachLabel.setText("Auto-Balancer Alert: Worker thresholds breached to satisfy task demand (Breached: "
                    + String.join(", ", breached) + ").");
        }
    }

    private void rebuildWorkerCards() {
        if (workerCardsPane == null || viewModel == null) return;
        workerCardsPane.getChildren().clear();
        cardMap.clear();
        for (var worker : viewModel.getWorkers()) {
            addWorkerCard(worker);
        }
    }

    private void addWorkerCard(com.legatus.studio.model.WorkerNode worker) {
        if (worker == null || cardMap.containsKey(worker.getWorkerId())) return;
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/legatus/studio/worker_card_cell.fxml")
            );
            javafx.scene.Node card = loader.load();
            WorkerCardCellController controller = loader.getController();
            controller.updateWorker(worker);
            cardMap.put(worker.getWorkerId(), card);
            workerCardsPane.getChildren().add(card);
        } catch (Exception ex) {
            // Silently fallback or log
        }
    }

    private void removeWorkerCard(com.legatus.studio.model.WorkerNode worker) {
        if (worker == null) return;
        javafx.scene.Node card = cardMap.remove(worker.getWorkerId());
        if (card != null && workerCardsPane != null) {
            workerCardsPane.getChildren().remove(card);
        }
    }
}
