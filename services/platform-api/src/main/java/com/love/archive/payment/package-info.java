@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::web",
                "common::security",
                "audit::application",
                "wechatpay::application"
        })
package com.love.archive.payment;
