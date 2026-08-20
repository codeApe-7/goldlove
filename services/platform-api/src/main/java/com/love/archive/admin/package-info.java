@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::web",
                "audit::application",
                "identity::security",
                "storage::application"
        })
package com.love.archive.admin;
