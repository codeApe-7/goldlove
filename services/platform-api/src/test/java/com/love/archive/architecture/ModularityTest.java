package com.love.archive.architecture;

import com.love.archive.PlatformApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesApplicationModuleBoundaries() {
        ApplicationModules.of(PlatformApiApplication.class).verify();
    }
}
