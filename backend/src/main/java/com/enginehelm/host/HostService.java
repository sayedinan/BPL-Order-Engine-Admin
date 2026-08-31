package com.enginehelm.host;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HostService {

    private final HostRepository hosts;

    public HostService(HostRepository hosts) {
        this.hosts = hosts;
    }

    @Transactional(readOnly = true)
    public List<HostSummaryDto> list() {
        return hosts.findAll().stream().map(HostSummaryDto::from).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public Host add(Host h) {
        return hosts.save(h);
    }
}
