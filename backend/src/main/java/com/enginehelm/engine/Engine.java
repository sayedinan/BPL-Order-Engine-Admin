package com.enginehelm.engine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "engines")
public class Engine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Lob
    @Column(name = "start_script", nullable = false)
    private String startScript;

    @Lob
    @Column(name = "stop_script", nullable = false)
    private String stopScript;

    @Lob
    @Column(name = "status_script", nullable = false)
    private String statusScript;

    @Lob
    @Column(name = "log_script", nullable = false)
    private String logScript;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getHostId() { return hostId; }
    public void setHostId(Long hostId) { this.hostId = hostId; }
    public String getStartScript() { return startScript; }
    public void setStartScript(String startScript) { this.startScript = startScript; }
    public String getStopScript() { return stopScript; }
    public void setStopScript(String stopScript) { this.stopScript = stopScript; }
    public String getStatusScript() { return statusScript; }
    public void setStatusScript(String statusScript) { this.statusScript = statusScript; }
    public String getLogScript() { return logScript; }
    public void setLogScript(String logScript) { this.logScript = logScript; }
}
