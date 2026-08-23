@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::web",
                "common::security",
                "audit::application",
                "storage::application"
        })
package com.love.archive.course;
