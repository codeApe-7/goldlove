@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
                "common::web",
                "common::security",
                "audit::application",
                "identity::application",
                "identity::security",
                "guest::application",
                "consent::application",
                "payment::application"
        })
package com.love.archive.review;
