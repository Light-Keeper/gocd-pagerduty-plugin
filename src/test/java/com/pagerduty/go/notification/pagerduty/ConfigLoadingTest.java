package com.pagerduty.go.notification.pagerduty;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.testng.annotations.Test;
import org.testng.Assert;

import java.io.File;

public class ConfigLoadingTest {

    @Test
    public void testBasicConfigLoad() {
        Config config = ConfigFactory.load("configReferenceTest.conf");

        Assert.assertEquals(config.getStringList("pagerduty.statuses_to_alert").get(0), "Failed");
    }
}
