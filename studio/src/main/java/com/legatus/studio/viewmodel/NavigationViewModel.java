package com.legatus.studio.viewmodel;

import com.legatus.studio.model.QuarantineEnrollment;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.collections.ObservableList;

import java.util.Objects;

/**
 * ViewModel driving the Left Navigation Rail and dynamic badge counters.
 */
public class NavigationViewModel {

    private final MainViewModel mainViewModel;
    private final IntegerBinding quarantineBadgeCount;

    public NavigationViewModel(MainViewModel mainViewModel, ObservableList<QuarantineEnrollment> enrollments) {
        this.mainViewModel = Objects.requireNonNull(mainViewModel, "mainViewModel must not be null");
        Objects.requireNonNull(enrollments, "enrollments must not be null");

        this.quarantineBadgeCount = Bindings.size(enrollments);
    }

    public void selectModule(MainViewModel.NavigationModule module) {
        mainViewModel.navigateTo(module);
    }

    public MainViewModel getMainViewModel() {
        return mainViewModel;
    }

    public IntegerBinding quarantineBadgeCountProperty() {
        return quarantineBadgeCount;
    }
}
