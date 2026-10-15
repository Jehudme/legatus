package com.legatus.studio.viewmodel;

import com.legatus.studio.model.ClusterMetrics;
import com.legatus.studio.transport.LstcpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MainAndRibbonViewModelTest {

    @Test
    void testMainViewModelNavigationAndDrawer() {
        MainViewModel vm = new MainViewModel();
        assertEquals(MainViewModel.NavigationModule.OVERVIEW, vm.getCurrentModule());
        assertTrue(vm.isIsDrawerExpanded());

        vm.navigateTo(MainViewModel.NavigationModule.FLEET);
        assertEquals(MainViewModel.NavigationModule.FLEET, vm.getCurrentModule());

        vm.toggleDrawer();
        assertFalse(vm.isIsDrawerExpanded());

        vm.updateConnectionState(LstcpClient.State.STREAMING);
        assertTrue(vm.isIsConnected());
        assertEquals("STREAMING", vm.getConnectionStatusText());

        vm.updateConnectionState(LstcpClient.State.DISCONNECTED);
        assertFalse(vm.isIsConnected());
    }

    @Test
    void testRibbonViewModelBindings() {
        ClusterMetrics metrics = new ClusterMetrics();
        RibbonViewModel ribbon = new RibbonViewModel(metrics);

        metrics.setClusterCpuPercent(42.5);
        assertEquals("42.5%", ribbon.cpuFormattedProperty().get());

        metrics.setClusterUsedMemoryBytes(4L * 1024 * 1024 * 1024);
        metrics.setClusterTotalMemoryBytes(16L * 1024 * 1024 * 1024);
        assertEquals("4.0 / 16.0 GB", ribbon.memoryFormattedProperty().get());

        metrics.setOnlineWorkersCount(8);
        assertEquals("8", ribbon.workersFormattedProperty().get());

        metrics.setExecutingTasksCount(15);
        metrics.setQueuedTasksCount(3);
        assertEquals("15 active | 3 queued", ribbon.tasksThroughputFormattedProperty().get());

        metrics.setRttLatencyMs(4.2);
        assertEquals("4.2 ms", ribbon.rttFormattedProperty().get());
    }
}
