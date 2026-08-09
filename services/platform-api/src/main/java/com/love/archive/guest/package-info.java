@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::web",
                "common::security",
                "audit::application",
                "identity::application",
                "identity::security",
                "storage::application"
        })
package com.love.archive.guest;
