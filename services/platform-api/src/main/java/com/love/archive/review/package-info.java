@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
                "common::web",
                "common::security",
                "audit::application",
                "identity::application",
                "identity::security",
                "guest::application",
                "guest::domain",
                "consent::application",
                "payment::application",
                "storage::application"
        })
package com.love.archive.review;
