package com.example.badukanalyzer.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class AnalysisJobStore {

    public enum Status { RUNNING, DONE, ERROR }

    public static class Job {
        public final Status status;
        public final String resultId;
        public final String error;
        public final int progress; // 0~100
        public final String fileName;

        private Job(Status status, String resultId, String error, int progress, String fileName) {
            this.status = status;
            this.resultId = resultId;
            this.error = error;
            this.progress = progress;
            this.fileName = fileName;
        }

        public static Job running(String fileName) { return new Job(Status.RUNNING, null, null, 0, fileName); }
        public static Job done(String resultId)    { return new Job(Status.DONE, resultId, null, 100, null); }
        public static Job error(String msg)        { return new Job(Status.ERROR, null, msg, 0, null); }
    }

    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public void put(String jobId, Job job) { jobs.put(jobId, job); }
    public Job get(String jobId) { return jobs.get(jobId); }

    public void updateProgress(String jobId, int progress) {
        Job existing = jobs.get(jobId);
        String fn = existing != null ? existing.fileName : null;
        jobs.put(jobId, new Job(Status.RUNNING, null, null, progress, fn));
    }

    public List<Job> getRunningJobs() {
        return jobs.values().stream()
                .filter(j -> j.status == Status.RUNNING && j.fileName != null)
                .collect(Collectors.toList());
    }
}
