package com.pagerduty.go.notification.pagerduty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.google.gson.GsonBuilder;
import com.squareup.pagerduty.incidents.FakePagerDuty;
import com.squareup.pagerduty.incidents.PagerDuty;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.Assert;


import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

public class PagerDutyHandlerTest {
    private String hostname;

    @BeforeClass
    private void Setup() {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "";
        }
    }

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

        pagerDutyHandlerWithFakePagerDuty.clearCurrentIncidents();
        pagerDutyHandlerWithFakePagerDuty.handle(message);

        Assert.assertTrue(fakePD.openIncidents().size() == 1);
        assertThat(fakePD.openIncidents().values().iterator().next(), startsWith(getExpectedMessage(message)));
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

        pagerDutyHandlerWithFakePagerDuty.clearCurrentIncidents();
        // Send the failure, incident should be opened
        pagerDutyHandlerWithFakePagerDuty.handle(failureMessage);

        Assert.assertTrue(fakePD.openIncidents().size() == 1);
        assertThat(fakePD.openIncidents().values().iterator().next(), startsWith(getExpectedMessage(failureMessage)));

        // Send the pass, incident should be resolved
        pagerDutyHandlerWithFakePagerDuty.handle(passedMessage);

        Assert.assertTrue(fakePD.openIncidents().size() == 0);
        Assert.assertTrue(fakePD.closedIncidents().size() == 1);
        assertThat(fakePD.closedIncidents().values().iterator().next(), startsWith(getExpectedMessage(failureMessage)));
    }

    @Test
    public void testHandleMultipleFailuresAndResolutions() throws Exception {

        Config config = ConfigFactory.load("configReferenceTest_Multiple.conf");
        String goFailedMessage1 = getTestFile("/goMessageFailure1.json");
        GoNotificationMessage failureMessage1 = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goFailedMessage1, GoNotificationMessage.class);
        String goPassedMessage1 = getTestFile("/goMessagePassed1.json");
        GoNotificationMessage passedMessage1 = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goPassedMessage1, GoNotificationMessage.class);
        String goFailedMessage2 = getTestFile("/goMessageFailure2.json");
        GoNotificationMessage failureMessage2 = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goFailedMessage2, GoNotificationMessage.class);
        String goFailedMessage3 = getTestFile("/goMessageFailure3.json");
        GoNotificationMessage failureMessage3 = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goFailedMessage3, GoNotificationMessage.class);
        String goPassedMessage3 = getTestFile("/goMessagePassed3.json");
        GoNotificationMessage passedMessage3 = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goPassedMessage3, GoNotificationMessage.class);

        final FakePagerDuty fakePD = new FakePagerDuty();

        PagerDutyHandler pagerDutyHandlerWithFakePagerDuty = new PagerDutyHandler(config) {
            @Override
            protected PagerDuty newPagerDuty(String apiKey) {
                return fakePD;
            }
        };

        pagerDutyHandlerWithFakePagerDuty.clearCurrentIncidents();
        // Send the failure, incident should be opened
        pagerDutyHandlerWithFakePagerDuty.handle(failureMessage1);

        assertThat(fakePD.openIncidents().size(), comparesEqualTo(1));
        assertThat(fakePD.openIncidents(), hasValue(equalTo(getExpectedMessage(failureMessage1))));

        pagerDutyHandlerWithFakePagerDuty.handle(failureMessage2);

        assertThat(fakePD.openIncidents().size(), comparesEqualTo(2));
        assertThat(fakePD.openIncidents(), hasValue(equalTo(getExpectedMessage(failureMessage2))));

        // Send passing build for failure 1
        pagerDutyHandlerWithFakePagerDuty.handle(passedMessage1);

        // Failure 1 should be resolved and the message shouldn't be in open incidents
        assertThat(fakePD.openIncidents().size(), comparesEqualTo(1));
        assertThat(fakePD.closedIncidents().size(), comparesEqualTo(1));
        // Failure 1 is not in open incidents but is in closed incidents
        assertThat(fakePD.openIncidents(), not(hasValue(equalTo(getExpectedMessage(failureMessage1)))));
        assertThat(fakePD.closedIncidents(), hasValue(equalTo(getExpectedMessage(failureMessage1))));
        // Failure 2 is still in open incidents
        assertThat(fakePD.openIncidents(), hasValue(equalTo(getExpectedMessage(failureMessage2))));

        pagerDutyHandlerWithFakePagerDuty.handle(failureMessage3);

        assertThat(fakePD.openIncidents().size(), comparesEqualTo(2));
        assertThat(fakePD.closedIncidents().size(), comparesEqualTo(1));
        assertThat(fakePD.openIncidents(), hasValue(equalTo(getExpectedMessage(failureMessage3))));

        pagerDutyHandlerWithFakePagerDuty.handle(passedMessage3);

        assertThat(fakePD.openIncidents().size(), comparesEqualTo(1));
        assertThat(fakePD.closedIncidents().size(), comparesEqualTo(2));
        assertThat(fakePD.openIncidents(), hasValue(equalTo(getExpectedMessage(failureMessage2))));
        assertThat(fakePD.closedIncidents(), hasValue(equalTo(getExpectedMessage(failureMessage3))));

    }

    String getTestFile(String filename) throws URISyntaxException, IOException {
        Path filePath = Paths.get(getClass().getResource(filename).toURI());
        return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
    }

    String getExpectedMessage(GoNotificationMessage message) {
        return String.format("Failed Build: %s build %s on %s", message.fullyQualifiedJobName(), message.getStageState(), hostname);
    }
}
