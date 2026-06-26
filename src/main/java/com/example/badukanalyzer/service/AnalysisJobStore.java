package com.example.badukanalyzer.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnalysisJobStore {

    public enum Status { RUNNING, DONE, ERROR }

    public static class Job {
        public final Status status;
        public final String resultId;
        public final String error;

        private Job(Status status, String resultId, String error) {
            this.status = status;
            this.resultId = resultId;
            this.error = error;
        }

        public static Job running() { return new Job(Status.RUNNING, null, null); }
        public static Job done(String resultId) { return new Job(Status.DONE, resultId, null); }
        public static Job error(String msg) { return new Job(Status.ERROR, null, msg); }
    }

    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public void put(String jobId, Job job) { jobs.put(jobId, job); }
    public Job get(String jobId) { return jobs.get(jobId); }
}
