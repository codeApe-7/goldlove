@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::web",
                "common::security",
                "audit::application"
        })
package com.love.archive.payment;
