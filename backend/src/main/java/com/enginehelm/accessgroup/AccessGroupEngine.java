package com.enginehelm.accessgroup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "access_group_engines")
public class AccessGroupEngine {

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "engine_id", nullable = false)
    private Long engineId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getEngineId() { return engineId; }
    public void setEngineId(Long engineId) { this.engineId = engineId; }
}
