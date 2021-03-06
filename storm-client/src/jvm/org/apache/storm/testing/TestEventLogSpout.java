/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package org.apache.storm.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.storm.utils.Utils.get;

public class TestEventLogSpout extends BaseRichSpout implements CompletableSpout {
    private static final Map<String, Integer> acked = new HashMap<String, Integer>();
    private static final Map<String, Integer> failed = new HashMap<String, Integer>();
    public static Logger LOG = LoggerFactory.getLogger(TestEventLogSpout.class);
    SpoutOutputCollector _collector;
    private String uid;
    private long totalCount;
    private long eventId = 0;
    private long myCount;
    private int source;

    public TestEventLogSpout(long totalCount) {
        this.uid = UUID.randomUUID().toString();

        synchronized (acked) {
            acked.put(uid, 0);
        }
        synchronized (failed) {
            failed.put(uid, 0);
        }

        this.totalCount = totalCount;
    }

    public static int getNumAcked(String stormId) {
        synchronized (acked) {
            return get(acked, stormId, 0);
        }
    }

    public static int getNumFailed(String stormId) {
        synchronized (failed) {
            return get(failed, stormId, 0);
        }
    }

    @Override
    public void open(Map<String, Object> conf, TopologyContext context, SpoutOutputCollector collector) {
        _collector = collector;
        this.source = context.getThisTaskId();
        long taskCount = context.getComponentTasks(context.getThisComponentId()).size();
        myCount = totalCount / taskCount;
    }

    @Override
    public void close() {

    }

    public void cleanup() {
        synchronized (acked) {
            acked.remove(uid);
        }
        synchronized (failed) {
            failed.remove(uid);
        }
    }

    public boolean completed() {

        int ackedAmt;
        int failedAmt;

        synchronized (acked) {
            ackedAmt = acked.get(uid);
        }
        synchronized (failed) {
            failedAmt = failed.get(uid);
        }
        int totalEmitted = ackedAmt + failedAmt;

        if (totalEmitted >= totalCount) {
            return true;
        }
        return false;
    }

    @Override
    public void nextTuple() {
        if (eventId < myCount) {
            eventId++;
            _collector.emit(new Values(source, eventId), eventId);
        }
    }

    @Override
    public void ack(Object msgId) {
        synchronized (acked) {
            int curr = get(acked, uid, 0);
            acked.put(uid, curr + 1);
        }
    }

    @Override
    public void fail(Object msgId) {
        synchronized (failed) {
            int curr = get(failed, uid, 0);
            failed.put(uid, curr + 1);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("source", "eventId"));
    }

    @Override
    public boolean isExhausted() {
        return completed();
    }
}
