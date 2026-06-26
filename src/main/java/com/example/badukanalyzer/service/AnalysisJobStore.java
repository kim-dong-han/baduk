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
        public final int progress; // 0~100

        private Job(Status status, String resultId, String error, int progress) {
            this.status = status;
            this.resultId = resultId;
            this.error = error;
            this.progress = progress;
        }

        public static Job running()                { return new Job(Status.RUNNING, null, null, 0); }
        public static Job running(int progress)    { return new Job(Status.RUNNING, null, null, progress); }
        public static Job done(String resultId)    { return new Job(Status.DONE, resultId, null, 100); }
        public static Job error(String msg)        { return new Job(Status.ERROR, null, msg, 0); }
    }

    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public void put(String jobId, Job job) { jobs.put(jobId, job); }
    public Job get(String jobId) { return jobs.get(jobId); }
    public void updateProgress(String jobId, int progress) { jobs.put(jobId, Job.running(progress)); }
}
