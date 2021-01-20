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

import java.io.Serializable;
import java.util.Date;

@SuppressWarnings("serial")
public class BbExecutionStatistics implements Serializable {
	
	private Date started;
	
	private Date finished;
	
	private long numPassedTcs;

    private long numCondPassedTcs;

	private long numFailedTcs;
	
	private long numInitFailed;

    private long numNonPreparedScripts;

	public BbExecutionStatistics() {

	}

	public BbExecutionStatistics(BbExecutionStatistics toClone) {

		this.started = toClone.started;
		this.finished = toClone.finished;

		this.numPassedTcs = toClone.numPassedTcs;
        this.numCondPassedTcs = toClone.numCondPassedTcs;
		this.numFailedTcs = toClone.numFailedTcs;
		this.numInitFailed = toClone.numInitFailed;
        this.numNonPreparedScripts = toClone.numNonPreparedScripts;
	}

    public void incNumPassedTcs(long value) {
		this.numPassedTcs += value;
	}

    public void incNumCondPassedTcs(long value) {
        this.numCondPassedTcs += value;
    }

    public void incNumFailedTcs(long value) {
		this.numFailedTcs += value;
	}

	public void incNumInitFailed() {
		this.numInitFailed++;
	}

    public void incNumNonPreparedScripts(long value) {
        this.numNonPreparedScripts += value;
    }

	public Date getStarted() {
		return started;
	}

	public void setStarted(Date started) {
		this.started = started;
	}

	public Date getFinished() {
		return finished;
	}

	public void setFinished(Date finished) {
		this.finished = finished;
	}

	public long getNumPassedTcs() {
		return numPassedTcs;
	}

	public long getNumFailedTcs() {
		return numFailedTcs;
	}

	public long getNumInitFailed() {
		return numInitFailed;
	}

    public long getNumCondPassedTcs() {
        return numCondPassedTcs;
    }

    public long getNumNonPreparedScripts() {
        return numNonPreparedScripts;
    }
}
