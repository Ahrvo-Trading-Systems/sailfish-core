/******************************************************************************
 * Copyright 2009-2018 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.exactpro.sf.bigbutton.execution;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.SFAPIClient;
import com.exactpro.sf.bigbutton.RegressionRunner;
import com.exactpro.sf.bigbutton.importing.ErrorType;
import com.exactpro.sf.bigbutton.importing.ImportError;
import com.exactpro.sf.bigbutton.importing.LibraryImportResult;
import com.exactpro.sf.bigbutton.library.Executor;
import com.exactpro.sf.bigbutton.library.Library;
import com.exactpro.sf.bigbutton.library.Script;
import com.exactpro.sf.bigbutton.library.ScriptList;
import com.exactpro.sf.embedded.statistics.entities.SfInstance;
import com.exactpro.sf.scriptrunner.StatusType;

public class ExecutionProgressMonitor {

	private static final Logger logger = LoggerFactory.getLogger(ExecutionProgressMonitor.class);

	private final List<ExecutorClient> allExecutors = new ArrayList<>();

    private final List<ScriptList> enqueued = new ArrayList<>();

    private final Map<Executor, List<ScriptList>> executed = new HashMap<>();

    private final List<ScriptList> rejected = new ArrayList<>();

    private final Map<Executor, ScriptList> running = new HashMap<>();

	private long totalListsCount;

	private long executedListsCount;

	private long totalScriptsCount;

	private long executedScriptsCount;

	private final RegressionRunner runner;

    private volatile boolean finished;

	private BigButtonExecutionStatus status = BigButtonExecutionStatus.Inactive;

    private StatusType executionStatus = StatusType.NA;

	private String errorText;

    private final List<String> warns = new ArrayList<>();

	private ExecutionReportExporter reportExporter;

    private int numExecutorsInError;

	private ExecutorService taskExecutor;

    private final BbExecutionStatistics executionStatistics = new BbExecutionStatistics();

	private ExecutorsStatusChecker statusChecker;

	private Library library;

    private Map<ErrorType, Set<ImportError>> importErrors;

	public ExecutionProgressMonitor(RegressionRunner runner) {

		this.runner = runner;

	}

	/*
	private List<ScriptList> getExecutedView(List<ScriptList> fullList, int limit) {

		int beginIndex = (fullList.size() > limit ? fullList.size() - 1 - limit : 0);

		return new ArrayList<>(fullList.subList(beginIndex, fullList.size()));

	}
	*/

	private void doTearDown() {

        identifyExecutionStatus();

        if(reportExporter != null) {
            writeReport();
            reportExporter.writeCompleted();
		}

        if(taskExecutor != null) {
            taskExecutor.shutdown();
		}

        if(statusChecker != null) {
            statusChecker.stop();
		}

        executionStatistics.setFinished(new Date());
		this.finished = true;
        this.importErrors = null;
        try {
            runner.close();
        } catch (Exception e) {
            logger.error("Error while closing RegressionRunner", e);
        }


	}

	public synchronized void preparing() {
		this.status = BigButtonExecutionStatus.Preparing;
	}

    public synchronized void ready(LibraryImportResult importResult) {

        if(status != BigButtonExecutionStatus.Error) {
            this.status = BigButtonExecutionStatus.Ready;
        }

        this.library = importResult.getLibrary();

        this.importErrors = new ConcurrentHashMap<>();

        importErrors.put(ErrorType.COMMON, importResult.getCommonErrors());
        importErrors.put(ErrorType.GLOBALS, importResult.getGlobalsErrors());
        importErrors.put(ErrorType.EXECUTOR, importResult.getExecutorErrors());
        importErrors.put(ErrorType.SCRIPTLIST, importResult.getScriptListErrors());

		this.statusChecker = new ExecutorsStatusChecker();

        Thread statusCheckerThread = new Thread(statusChecker, "BB slaves status checker");
		statusCheckerThread.setDaemon(true);
		statusCheckerThread.start();

	}

	public synchronized void started() {

		try {

            executionStatistics.setStarted(new Date());
            this.taskExecutor = Executors.newFixedThreadPool(allExecutors.size());
			this.reportExporter = new ExecutionReportExporter();

		} catch (IOException e) {
			throw new RuntimeException("Report exporter could not be created", e);
		}

		this.status = BigButtonExecutionStatus.Running;
	}

    public synchronized void error(String text) {
        logger.error("BigButton execution error:" + text);
        this.status = BigButtonExecutionStatus.Error;
        this.errorText = text;

        doTearDown();
	}

    public synchronized void warn(String text) {
        warns.add(text);
    }

	public synchronized void listEnqueued(ScriptList list) {

        enqueued.add(list);

		this.totalListsCount++;

		this.totalScriptsCount += list.getScripts().size();

	}

    public synchronized void listNonPrepared(ScriptList list) {
        list.setStatus(ScriptList.ScriptListStatus.SKIPPED);
        executionStatistics.incNumNonPreparedScripts(list.getScripts().size());
    }

	public synchronized void decreaseTotalScriptCount(int count) {

		this.totalScriptsCount -= count;

	}

	public synchronized void listTaken(ScriptList list, Executor executor) {

		for(int i = enqueued.size() -1; i >= 0; i--) {

            if(enqueued.get(i).equals(list)) {

                enqueued.remove(i);

                running.put(executor, list);

				return;

			}

		}

		throw new IllegalStateException("Taken " + list + " not found in queue!");

	}

	public synchronized void listExecuted(Executor executor, ScriptList list) {

        running.remove(executor);

        if(!executed.containsKey(executor)) {
            executed.put(executor, new ArrayList<ScriptList>());
		}

        executed.get(executor).add(0, list);

		this.executedListsCount++;

        if(finished) {
			return;
		}

        if(executedListsCount == totalListsCount) {
			this.status = BigButtonExecutionStatus.DownloadingReports;
			//doTearDown();
            taskExecutor.submit(new DownloadsCompleteTask(this));

		}

	}

	private List<ScriptList> getLimitedListView(List<ScriptList> fullList, int viewLimit) {

		List<ScriptList> enqueuedView = new ArrayList<>();

		int stopIndex = fullList.size() < viewLimit ? fullList.size(): viewLimit;

		for(int i = 0; i < stopIndex; i++) {

			enqueuedView.add(fullList.get(i));

		}

		return enqueuedView;

	}

	public synchronized ProgressView getCurrentProgressView(int inQueueLimit, int outQueueLimit) {

		ProgressView result = new ProgressView();

		List<ScriptList> enqueuedView = getLimitedListView(enqueued, inQueueLimit);
        List<ScriptList> rejectedView = getLimitedListView(rejected, inQueueLimit - enqueuedView.size() + 1);

		result.setEnqueued(enqueuedView);
		result.setNumInQueue(enqueued.size() - enqueuedView.size());
        result.setRejected(rejectedView);
        result.setNumRejected(rejected.size() - rejectedView.size());

		Map<Executor, List<ScriptList>> executedView = new HashMap<>();
		Map<Executor, Long> executedSizes = new HashMap<>();

        for(Entry<Executor, List<ScriptList>> entry : executed.entrySet()) {

			List<ScriptList> fullList = entry.getValue();
			List<ScriptList> listView = getLimitedListView(fullList, outQueueLimit);
			long numInQueue = fullList.size() - listView.size();

			executedView.put(entry.getKey(), listView);
			executedSizes.put(entry.getKey(), numInQueue);

		}

		result.setExecuted(executedView);
		result.setNumExecuted(executedSizes);

        result.setRunning(new HashMap<>(running));

		result.setCurrentTotalProgressPercent(
				RegressionRunnerUtils.calcPercent(executedScriptsCount, totalScriptsCount));

		result.setAllExecutors(allExecutors);

		result.setFinished(finished);
		result.setStatus(status);
        result.setExecutionStatus(executionStatus);

        result.setErrorText(errorText);
        result.setWarns(warns);

		if(library != null) {
			result.setLibraryFileName(library.getDescriptorFileName());
		}
		result.setLibrary(library);

        result.setImportErrors(importErrors);

        if(finished && reportExporter != null) {
            result.setReportFile(reportExporter.getFile());
		}

        if(finished) {

            result.setExecutionStatistics(new BbExecutionStatistics(executionStatistics));

		}

		return result;

	}

    public synchronized void executorClientEncounteredError(ExecutorClient client) {

		this.numExecutorsInError++;

        if(numExecutorsInError == allExecutors.size()) {
            error("All executors gone to 'Error' state");
		}

	}

    public synchronized void interrupt(String message) {

        if(status == BigButtonExecutionStatus.Inactive
                || status == BigButtonExecutionStatus.Finished) {

			return;

		}

        error(message);

	}

    public synchronized void runPaused() {
	    this.status = BigButtonExecutionStatus.Pause;
    }

    public synchronized void resumeRun(){
	    this.status = BigButtonExecutionStatus.Running;
    }

	public synchronized void reportsProcessingFinished() {
        doTearDown();

        this.status = BigButtonExecutionStatus.Finished;
	}

	public synchronized void scriptExecuted(Script script, int scriptRunId, Executor executor, String reportsFolder,
                                            boolean downloadNeded, boolean isMasterSf) throws Exception {

		this.executedScriptsCount++;

        ScriptExecutionStatistics statistics = script.getStatistics();

        executionStatistics.incNumPassedTcs(statistics.getNumPassed());
        executionStatistics.incNumCondPassedTcs(statistics.getNumConditionallyPassed());
        executionStatistics.incNumFailedTcs(statistics.getNumFailed());

        if (statistics.isExecutionFailed()) {
            executionStatistics.incNumInitFailed();
		}

        if(finished || isMasterSf) {
			return;
		}

        SFAPIClient apiClient = new SFAPIClient(URI.create(executor.getHttpUrl() + "/sfapi").normalize().toString());

        SfInstance currentSfInstance = runner.getCurrentSfInstance();
        Long sfCurrentID = currentSfInstance == null ? null : currentSfInstance.getId();

        ReportDownloadTask task = new ReportDownloadTask(runner.getWorkspaceDispatcher(), scriptRunId, reportsFolder,
                apiClient, downloadNeded, sfCurrentID);

        taskExecutor.submit(task);

	}

    public synchronized void rejectScriptList(ScriptList list) {
        ListExecutionStatistics statistics = list.getExecutionStatistics();
        statistics.setRejected(true);
        for (Script script : list.getScripts()) {
            script.getStatistics().setStatus("REJECTED");
        }
        rejected.add(list);
    }

	public List<ExecutorClient> getAllExecutors() {
		return allExecutors;
	}

    private void writeReport() {
        try {

            writeCollection(rejected);

            for(List<ScriptList> scriptLists : executed.values()){
                writeCollection(scriptLists);
            }

            writeCollection(running.values());

            writeCollection(enqueued);

        } catch (IOException e) {
            throw new RuntimeException("Write to report failed", e);
        }
    }

    private void writeCollection(Collection<ScriptList> scriptLists) throws IOException {
        for (ScriptList list : scriptLists) {
            reportExporter.writeList(list);
        }
    }

    private void identifyExecutionStatus() {
        if (errorText != null) {
            return;
        }

        if (executionStatistics.getNumFailedTcs() > 0
                || executionStatistics.getNumInitFailed() > 0
                || executionStatistics.getNumNonPreparedScripts() > 0) {
            executionStatus = StatusType.FAILED;
        } else if (executionStatistics.getNumCondPassedTcs() > 0) {
            executionStatus = StatusType.CONDITIONALLY_PASSED;
        } else {
            executionStatus = StatusType.PASSED;
        }
    }

    public Set<String> getAvailableExecutorNames() {
        return  getAllExecutors()
                .stream()
                .filter(client -> Boolean.TRUE.equals(client.getExecutorReady()))
                .map(client -> client.getExecutor().getName())
                .collect(Collectors.toSet());
    }

	private class ExecutorsStatusChecker implements Runnable {

		private volatile boolean running = true;

		private static final long CHECK_INTERVAL = 5000l;

		public void stop() {
			this.running = false;
		}

		private boolean pingUrl(URI url, int timeout) {

		    try {

		        HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
		        connection.setConnectTimeout(timeout);
		        connection.setReadTimeout(timeout);
		        connection.setRequestMethod("HEAD");
		        int responseCode = connection.getResponseCode();
                return responseCode >= 200 && responseCode <= 399;

		    } catch (IOException e) {
		        return false;
		    }

		}

		private void checkAndSetCurrentStatus(ExecutorClient client) {

			boolean available = pingUrl(URI.create(client.getExecutor().getHttpUrl() + "/sfapi/testscriptruns").normalize(), 3000);
			
			client.setExecutorReady(available);
			
		}
		
		@Override
		public void run() {
			
			while(running) {
				
				List<ExecutorClient> exClients = getAllExecutors();
				
				if(exClients != null) {
				
					for(ExecutorClient client : exClients) {
						
						checkAndSetCurrentStatus(client);
						
					}
				
				}
				
				try {
					
					Thread.sleep(CHECK_INTERVAL);
					
				} catch (InterruptedException e) {
					logger.error("Interrupted", e);
					break;
				}
				
			}
			
		}
		
	}
	
}
