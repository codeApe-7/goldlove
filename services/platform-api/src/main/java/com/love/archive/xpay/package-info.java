@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {"common::web", "payment::application", "payment::domain"})
package com.love.archive.xpay;
