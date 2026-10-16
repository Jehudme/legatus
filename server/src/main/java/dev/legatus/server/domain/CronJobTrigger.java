package dev.legatus.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Persistent dynamic cron triggers for scheduled Solix function execution (Table: legatus_cron_triggers).
 */
@Entity
@Table(name = "legatus_cron_triggers")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CronJobTrigger implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Size(max = 64)
    @Id
    @Column(name = "id", length = 64, nullable = false, unique = true)
    private String id;

    @NotNull
    @Size(max = 128)
    @Column(name = "cron_expression", length = 128, nullable = false)
    private String cronExpression;

    @NotNull
    @Size(max = 64)
    @Column(name = "function_id", length = 64, nullable = false)
    private String functionId;

    @Size(max = 64)
    @Column(name = "execution_profile_id", length = 64)
    private String executionProfileId;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @NotNull
    @Column(name = "total_invocations", nullable = false)
    private Long totalInvocations = 0L;

    @Size(max = 64)
    @Column(name = "created_by", length = 64)
    private String createdBy;

    public CronJobTrigger() {}

    public CronJobTrigger(String id, String cronExpression, String functionId, String executionProfileId, String createdBy) {
        this.id = id;
        this.cronExpression = cronExpression;
        this.functionId = functionId;
        this.executionProfileId = executionProfileId;
        this.createdBy = createdBy;
        this.active = true;
        this.createdAt = Instant.now();
        this.totalInvocations = 0L;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CronJobTrigger id(String id) {
        this.id = id;
        return this;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public CronJobTrigger cronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public CronJobTrigger functionId(String functionId) {
        this.functionId = functionId;
        return this;
    }

    public String getExecutionProfileId() {
        return executionProfileId;
    }

    public void setExecutionProfileId(String executionProfileId) {
        this.executionProfileId = executionProfileId;
    }

    public CronJobTrigger executionProfileId(String executionProfileId) {
        this.executionProfileId = executionProfileId;
        return this;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public CronJobTrigger active(Boolean active) {
        this.active = active;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public CronJobTrigger createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public CronJobTrigger lastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
        return this;
    }

    public Long getTotalInvocations() {
        return totalInvocations;
    }

    public void setTotalInvocations(Long totalInvocations) {
        this.totalInvocations = totalInvocations;
    }

    public CronJobTrigger totalInvocations(Long totalInvocations) {
        this.totalInvocations = totalInvocations;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public CronJobTrigger createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CronJobTrigger that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CronJobTrigger{" +
            "id='" + id + '\'' +
            ", cronExpression='" + cronExpression + '\'' +
            ", functionId='" + functionId + '\'' +
            ", executionProfileId='" + executionProfileId + '\'' +
            ", active=" + active +
            ", totalInvocations=" + totalInvocations +
            '}';
    }
}
