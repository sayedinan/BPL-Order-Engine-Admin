package com.enginehelm.host;

/**
 * Host projection for the engine-add dropdown. The frontend formats
 * {@code alias — hostnameOrIp:port} for display.
 */
public record HostSummaryDto(Long id, String alias, String hostnameOrIp, int port) {
    public static HostSummaryDto from(Host h) {
        return new HostSummaryDto(h.getId(), h.getAlias(), h.getHostnameOrIp(), h.getPort());
    }
}
