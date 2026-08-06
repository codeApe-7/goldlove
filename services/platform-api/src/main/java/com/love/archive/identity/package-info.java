@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::security", "common::web", "audit::application", "consent::application", "payment::application"
        })
package com.love.archive.identity;
