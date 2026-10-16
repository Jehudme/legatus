package dev.legatus.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Cluster-wide dynamic settings (Table: cluster_settings)
 */
@Entity
@Table(name = "cluster_settings")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ClusterSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Size(max = 64)
    @Id
    @Column(name = "setting_key", length = 64, nullable = false, unique = true)
    private String settingKey;

    @NotNull
    @Size(max = 255)
    @Column(name = "setting_value", length = 255, nullable = false)
    private String settingValue;

    public ClusterSetting() {}

    public ClusterSetting(String settingKey, String settingValue) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClusterSetting that)) return false;
        return Objects.equals(settingKey, that.settingKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(settingKey);
    }

    @Override
    public String toString() {
        return "ClusterSetting{" +
            "settingKey='" + settingKey + '\'' +
            ", settingValue='" + settingValue + '\'' +
            '}';
    }
}
