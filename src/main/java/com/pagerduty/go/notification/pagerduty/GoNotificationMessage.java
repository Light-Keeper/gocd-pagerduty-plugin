package com.pagerduty.go.notification.pagerduty;


import com.google.gson.annotations.SerializedName;
import com.thoughtworks.go.plugin.api.logging.Logger;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GoNotificationMessage {

    static class Pipeline {
        @SerializedName("name")
        private String name;

        @SerializedName("counter")
        private String counter;

        @SerializedName("stage")
        private Stage stage;
    }

    static class Stage {
        @SerializedName("name")
        private String name;

        @SerializedName("counter")
        private String counter;

        @SerializedName("state")
        private String state;

        @SerializedName("result")
        private String result;

        @SerializedName("create-time")
        private String createTime;

        @SerializedName("last-transition-time")
        private String lastTransitionTime;

        @SerializedName("jobs")
        private List<Job> jobs;
    }

    static class Job {
        @SerializedName("name")
        private String name;

        @SerializedName("schedule-time")
        private String scheduleTime;

        @SerializedName("complete-time")
        private String completeTime;

        @SerializedName("state")
        private String state;

        @SerializedName("result")
        private String result;

        @SerializedName("agent-uuid")
        private String agentUuid;
    }

    @SerializedName("pipeline")
    private Pipeline pipeline;

    public String fullyQualifiedJobName() {
        return pipeline.name + "/" + pipeline.counter + "/" + pipeline.stage.name + "/" + pipeline.stage.counter;
    }

    public String getPipelineName() {
        return pipeline.name;
    }

    public String getPipelineCounter() {
        return pipeline.counter;
    }

    public String getStageName() {
        return pipeline.stage.name;
    }

    public String getStageCounter() {
        return pipeline.stage.counter;
    }

    public String getStageState() {
        return pipeline.stage.state;
    }

    public String getStageResult() {
        return pipeline.stage.result;
    }

    public Date getStageCreateTime() {
        return parseISO8601(pipeline.stage.createTime);
    }

    public Date getStageLastTransitionTime() {
        return parseISO8601(pipeline.stage.lastTransitionTime);
    }

    public List<String> getJobNames() {
        List<String> jobNames = new ArrayList<String>();
        for (Job job : pipeline.stage.jobs) {
            jobNames.add(job.name);
        }
        return jobNames;
    }

    public Date getJobScheduleTime(String jobName) {
        Job job = getJob(jobName);
        return parseISO8601(job.scheduleTime);
    }

    public Date getJobCompleteTime(String jobName) {
        Job job = getJob(jobName);
        return parseISO8601(job.completeTime);
    }

    public String getJobState(String jobName) {
        Job job = getJob(jobName);
        return job.state;
    }

    public String getJobResult(String jobName) {
        Job job = getJob(jobName);
        return job.result;
    }

    private Job getJob(String jobName) {
        for (Job job : pipeline.stage.jobs) {
            if (jobName.equals(job.name)) {
                return job;
            }
        }
        return null;
    }

    private Date parseISO8601(String date) {
        DateFormat format = new SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return format.parse(date);
        } catch (ParseException e) {
            return null;
        }
    }
}
