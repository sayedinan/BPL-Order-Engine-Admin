package com.enginehelm.engine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EngineCreateRequest {

    @NotBlank
    private String name;

    @NotNull
    private Long hostId;

    @NotBlank
    private String startScript;

    @NotBlank
    private String stopScript;

    @NotBlank
    private String statusScript;

    @NotBlank
    private String logScript;

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
