package com.pagerduty.go.notification.pagerduty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.google.gson.GsonBuilder;
import com.squareup.pagerduty.incidents.FakePagerDuty;
import com.squareup.pagerduty.incidents.PagerDuty;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.testng.annotations.Test;
import org.testng.Assert;


import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

public class PagerDutyHandlerTest {

    @Test
    public void testHandleFailure() throws Exception {

        Config config = ConfigFactory.load("configReferenceTest.conf");
        String goNotification = getTestFile("/goMessageFailure1.json");
        GoNotificationMessage message = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goNotification, GoNotificationMessage.class);

        final FakePagerDuty fakePD = new FakePagerDuty();

        PagerDutyHandler pagerDutyHandlerWithFakePagerDuty = new PagerDutyHandler(config) {
            @Override
            protected PagerDuty newPagerDuty(String apiKey) {
                return fakePD;
            }
        };

        String expectedMessage = String.format("Failed Build: %s build %s on", message.fullyQualifiedJobName(), message.getStageState());

        pagerDutyHandlerWithFakePagerDuty.clearCurrentIncidents();
        pagerDutyHandlerWithFakePagerDuty.handle(message);

        Assert.assertTrue(fakePD.openIncidents().size() == 1);
        assertThat(fakePD.openIncidents().values().iterator().next(), startsWith(expectedMessage));
    }

    @Test
    public void testHandleFailureAndResolution() throws Exception {

        Config config = ConfigFactory.load("configReferenceTest.conf");
        String goFailedMessage = getTestFile("/goMessageFailure1.json");
        GoNotificationMessage failureMessage = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goFailedMessage, GoNotificationMessage.class);
        String goPassedMessage = getTestFile("/goMessagePassed1.json");
        GoNotificationMessage passedMessage = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goPassedMessage, GoNotificationMessage.class);

        final FakePagerDuty fakePD = new FakePagerDuty();

        PagerDutyHandler pagerDutyHandlerWithFakePagerDuty = new PagerDutyHandler(config) {
            @Override
            protected PagerDuty newPagerDuty(String apiKey) {
                return fakePD;
            }
        };

        String expectedMessage = String.format("Failed Build: %s build %s on", failureMessage.fullyQualifiedJobName(), failureMessage.getStageState());

        pagerDutyHandlerWithFakePagerDuty.clearCurrentIncidents();
        // Send the failure, incident should be opened
        pagerDutyHandlerWithFakePagerDuty.handle(failureMessage);

        Assert.assertTrue(fakePD.openIncidents().size() == 1);
        assertThat(fakePD.openIncidents().values().iterator().next(), startsWith(expectedMessage));

        // Send the pass, incident should be resolved
        pagerDutyHandlerWithFakePagerDuty.handle(passedMessage);

        Assert.assertTrue(fakePD.openIncidents().size() == 0);
        Assert.assertTrue(fakePD.closedIncidents().size() == 1);
        assertThat(fakePD.closedIncidents().values().iterator().next(), startsWith(expectedMessage));
    }

    String getTestFile(String filename) throws URISyntaxException, IOException {
        Path filePath = Paths.get(getClass().getResource(filename).toURI());
        return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
    }

    String getExpectedMessage(GoNotificationMessage message) {
        return String.format("Failed Build: %s build %s on", message.fullyQualifiedJobName(), message.getStageState());

    }

}
