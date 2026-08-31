package com.enginehelm.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class HostServiceTest {

    @Test
    void addIsSysAdminOnly() {
        // The @PreAuthorize annotation is what makes the service
        // sys.admin-only at runtime. The annotation must be present
        // on `add`. (Method security is enabled on
        // EngineHelmApplication via @EnableMethodSecurity.)
        Method add = null;
        for (Method m : HostService.class.getDeclaredMethods()) {
            if (m.getName().equals("add")) {
                add = m;
                break;
            }
        }
        assertThat(add).isNotNull();
        PreAuthorize ann = add.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("SYS_ADMIN");
    }

    @Test
    void listIsNotWriteProtected() {
        // Sanity: list() is reachable to admin role (the dropdown).
        Method list = null;
        for (Method m : HostService.class.getDeclaredMethods()) {
            if (m.getName().equals("list")) {
                list = m;
                break;
            }
        }
        assertThat(list).isNotNull();
        assertThat(list.getAnnotation(PreAuthorize.class)).isNull();
    }
}
