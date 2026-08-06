@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::web",
                "common::security",
                "audit::application",
                "payment::application",
                "identity::security"
        })
package com.love.archive.consent;
