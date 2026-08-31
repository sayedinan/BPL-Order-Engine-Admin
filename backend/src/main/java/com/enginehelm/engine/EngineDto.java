package com.enginehelm.engine;

import com.enginehelm.host.Host;

public record EngineDto(
        Long id,
        String name,
        Long hostId,
        String hostAlias,
        String hostnameOrIp,
        int port) {

    public static EngineDto from(Engine e, Host h) {
        return new EngineDto(
                e.getId(),
                e.getName(),
                e.getHostId(),
                h == null ? null : h.getAlias(),
                h == null ? null : h.getHostnameOrIp(),
                h == null ? 0 : h.getPort());
    }
}
