/*
 * Copyright (c) 2020 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.rss.clients;

import com.uber.rss.common.AppTaskAttemptId;
import com.uber.rss.common.ServerDetail;
import com.uber.rss.common.ServerReplicationGroup;
import com.uber.rss.exceptions.RssNetworkException;
import com.uber.rss.testutil.StreamServerTestUtils;
import com.uber.rss.testutil.TestConstants;
import com.uber.rss.testutil.TestStreamServer;
import com.uber.rss.util.ServerHostAndPort;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class MultiServerAsyncWriteClientTest {

    @DataProvider(name = "data-provider")
    public Object[][] dataProviderMethod2() {
        return new Object[][] { { 1, false, true, 1 }, { 2, true, false, 10 } };
    }

    @Test(dataProvider = "data-provider")
    public void writeAndReadRecords_noRecord(int numTestServers, boolean finishUploadAck, boolean usePooledConnection, int writeQueueSize) {
        List<TestStreamServer> testServers = new ArrayList<>();
        for (int i = 0; i < numTestServers; i++) {
            testServers.add(TestStreamServer.createRunningServer());
        }

        int numWriteThreads = 2;

        int numMaps = 1;
        AppTaskAttemptId appTaskAttemptId = new AppTaskAttemptId("app1", "exec1", 1, 2, 0L);

        List<ServerDetail> serverDetails = testServers.stream()
            .map(t->new ServerDetail(t.getServerId(), t.getRunningVersion(), t.getShuffleConnectionString()))
            .collect(Collectors.toList());

        try (MultiServerAsyncWriteClient writeClient = new MultiServerAsyncWriteClient(
                Arrays.asList(new ServerReplicationGroup(serverDetails)),
                TestConstants.NETWORK_TIMEOUT,
                TestConstants.NETWORK_TIMEOUT,
                finishUploadAck,
                usePooledConnection,
                writeQueueSize,
                numWriteThreads,
                "user1",
                appTaskAttemptId.getAppId(),
                appTaskAttemptId.getAppAttempt(),
                new ShuffleWriteConfig()
        )) {
            writeClient.connect();
            writeClient.startUpload(appTaskAttemptId, numMaps, 20);

            writeClient.finishUpload();

            for (TestStreamServer testServer : testServers) {
                List<RecordKeyValuePair> records = StreamServerTestUtils.readAllRecords2(testServer.getShufflePort(), appTaskAttemptId.getAppShuffleId(), 0, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), 0);

                records = StreamServerTestUtils.readAllRecords2(testServer.getShufflePort(), appTaskAttemptId.getAppShuffleId(), 1, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), 0);
            }
        } finally {
            testServers.forEach(t->t.shutdown());
        }
    }

    @Test(dataProvider = "data-provider", expectedExceptions = {RssNetworkException.class})
    public void connectInvalidServer(int numTestServers, boolean finishUploadAck, boolean usePooledConnection, int writeQueueSize) {
        List<TestStreamServer> testServers = new ArrayList<>();
        for (int i = 0; i < numTestServers; i++) {
            testServers.add(TestStreamServer.createRunningServer());
        }

        int numWriteThreads = 2;

        AppTaskAttemptId appTaskAttemptId = new AppTaskAttemptId("app1", "exec1", 1, 2, 0L);

        List<ServerDetail> serverDetails = Arrays.asList(new ServerDetail("server1", "12345", "invalid_server:80"));

        int networkTimeout = 1000;
        try (MultiServerAsyncWriteClient writeClient = new MultiServerAsyncWriteClient(
            Arrays.asList(new ServerReplicationGroup(serverDetails)),
            networkTimeout,
            networkTimeout,
            finishUploadAck,
            usePooledConnection,
            writeQueueSize,
            numWriteThreads,
            "user1",
            appTaskAttemptId.getAppId(),
            appTaskAttemptId.getAppAttempt(),
            new ShuffleWriteConfig()
        )) {
            writeClient.connect();
        } finally {
            testServers.forEach(t->t.shutdown());
        }
    }

    @Test(dataProvider = "data-provider")
    public void closeClientMultiTimes(int numTestServers, boolean finishUploadAck, boolean usePooledConnection, int writeQueueSize) {
        List<TestStreamServer> testServers = new ArrayList<>();
        for (int i = 0; i < numTestServers; i++) {
            testServers.add(TestStreamServer.createRunningServer());
        }

        int numWriteThreads = 2;

        int numMaps = 1;
        AppTaskAttemptId appTaskAttemptId = new AppTaskAttemptId("app1", "exec1", 1, 2, 0L);

        List<ServerDetail> serverDetails = testServers.stream()
            .map(t->new ServerDetail(t.getServerId(), t.getRunningVersion(), t.getShuffleConnectionString()))
            .collect(Collectors.toList());

        try (MultiServerAsyncWriteClient writeClient = new MultiServerAsyncWriteClient(
            Arrays.asList(new ServerReplicationGroup(serverDetails)),
            TestConstants.NETWORK_TIMEOUT,
            TestConstants.NETWORK_TIMEOUT,
            finishUploadAck,
            usePooledConnection,
            writeQueueSize,
            numWriteThreads,
            "user1",
            appTaskAttemptId.getAppId(),
            appTaskAttemptId.getAppAttempt(),
            new ShuffleWriteConfig()
        )) {
            writeClient.connect();
            writeClient.startUpload(appTaskAttemptId, numMaps, 20);

            writeClient.sendRecord(0, null, null);

            writeClient.finishUpload();

            writeClient.close();
            writeClient.close();
        } finally {
            testServers.forEach(t->t.shutdown());
        }
    }

    @Test(dataProvider = "data-provider")
    public void writeAndReadRecords(int numTestServers, boolean finishUploadAck, boolean usePooledConnection, int writeQueueSize) {
        List<TestStreamServer> testServers = new ArrayList<>();
        for (int i = 0; i < numTestServers; i++) {
            testServers.add(TestStreamServer.createRunningServer());
        }

        List<ServerDetail> serverDetails = testServers.stream()
            .map(t->new ServerDetail(t.getServerId(), t.getRunningVersion(), t.getShuffleConnectionString()))
            .collect(Collectors.toList());

        int numWriteThreads = 2;

        int numMaps = 1;
        AppTaskAttemptId appTaskAttemptId = new AppTaskAttemptId("app1", "exec1", 1, 2, 0L);

        try (MultiServerAsyncWriteClient writeClient = new MultiServerAsyncWriteClient(
            Arrays.asList(new ServerReplicationGroup(serverDetails)),
            TestConstants.NETWORK_TIMEOUT,
            TestConstants.NETWORK_TIMEOUT,
            finishUploadAck,
            usePooledConnection,
            writeQueueSize,
            numWriteThreads,
            "user1",
            appTaskAttemptId.getAppId(),
            appTaskAttemptId.getAppAttempt(),
            new ShuffleWriteConfig()
        )) {
            writeClient.connect();
            writeClient.startUpload(appTaskAttemptId, numMaps, 20);

            writeClient.sendRecord(0, null, null);

            writeClient.sendRecord(1,
                    ByteBuffer.wrap(new byte[0]),
                    ByteBuffer.wrap(new byte[0]));
            writeClient.sendRecord(1,
                    ByteBuffer.wrap(new byte[0]),
                    ByteBuffer.wrap(new byte[0]));

            writeClient.sendRecord(2,
                    null,
                    ByteBuffer.wrap("value1".getBytes(StandardCharsets.UTF_8)));
            writeClient.sendRecord(2,
                null,
                    ByteBuffer.wrap("value2".getBytes(StandardCharsets.UTF_8)));
            writeClient.sendRecord(2,
                null,
                    ByteBuffer.wrap("value3".getBytes(StandardCharsets.UTF_8)));

            writeClient.finishUpload();

            for (TestStreamServer testServer : testServers) {
                List<RecordKeyValuePair> records = StreamServerTestUtils.readAllRecords2(testServer.getShufflePort(), appTaskAttemptId.getAppShuffleId(), 0, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), 1);
                Assert.assertEquals(records.get(0).getKey(), null);
                Assert.assertEquals(records.get(0).getValue(), new byte[0]);

                records = StreamServerTestUtils.readAllRecords2(testServer.getShufflePort(), appTaskAttemptId.getAppShuffleId(), 1, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), 2);
                Assert.assertEquals(records.get(0).getKey(), null);
                Assert.assertEquals(records.get(0).getValue(), new byte[0]);
                Assert.assertEquals(records.get(1).getKey(), null);
                Assert.assertEquals(records.get(1).getValue(), new byte[0]);

                records = StreamServerTestUtils.readAllRecords2(testServer.getShufflePort(), appTaskAttemptId.getAppShuffleId(), 2, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), 3);
                Assert.assertEquals(records.get(0).getKey(), null);
                Assert.assertEquals(records.get(0).getValue(), "value1".getBytes(StandardCharsets.UTF_8));
                Assert.assertEquals(records.get(1).getKey(), null);
                Assert.assertEquals(records.get(1).getValue(), "value2".getBytes(StandardCharsets.UTF_8));
                Assert.assertEquals(records.get(2).getKey(), null);
                Assert.assertEquals(records.get(2).getValue(), "value3".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            testServers.forEach(t->t.shutdown());
        }
    }

    @Test(dataProvider = "data-provider")
    public void writeAndReadRecords_twoServerGroups(int numTestServers, boolean finishUploadAck, boolean usePooledConnection, int writeQueueSize) {
        if (numTestServers <= 1) {
            return;
        }

        List<TestStreamServer> testServers = new ArrayList<>();
        for (int i = 0; i < numTestServers; i++) {
            testServers.add(TestStreamServer.createRunningServer());
        }

        List<ServerDetail> serverDetails = testServers.stream()
            .map(t->new ServerDetail(t.getServerId(), t.getRunningVersion(), t.getShuffleConnectionString()))
            .collect(Collectors.toList());

        List<ServerDetail> group1 = new ArrayList<>();
        List<ServerDetail> group2 = new ArrayList<>();
        for (int i = 0; i < serverDetails.size(); i++) {
            if (i % 2 == 0) {
                group1.add(serverDetails.get(i));
            } else {
                group2.add(serverDetails.get(i));
            }
        }

        int numWriteThreads = 2;

        int numMaps = 1;
        AppTaskAttemptId appTaskAttemptId = new AppTaskAttemptId("app1", "exec1", 1, 2, 0L);

        int numRecords = 100000;

        try (MultiServerAsyncWriteClient writeClient = new MultiServerAsyncWriteClient(
            Arrays.asList(new ServerReplicationGroup(group1), new ServerReplicationGroup(group2)),
            TestConstants.NETWORK_TIMEOUT,
            TestConstants.NETWORK_TIMEOUT,
            finishUploadAck,
            usePooledConnection,
            writeQueueSize,
            numWriteThreads,
            "user1",
            appTaskAttemptId.getAppId(),
            appTaskAttemptId.getAppAttempt(),
            new ShuffleWriteConfig()
        )) {
            writeClient.connect();
            writeClient.startUpload(appTaskAttemptId, numMaps, 20);

            writeClient.sendRecord(0, null, null);

            writeClient.sendRecord(1,
                null,
                ByteBuffer.wrap(new byte[0]));
            writeClient.sendRecord(1,
                null,
                ByteBuffer.wrap(new byte[0]));

            for (int i = 0; i < numRecords; i ++) {
                writeClient.sendRecord(2,
                    null,
                    ByteBuffer.wrap(("value2_" + i).getBytes(StandardCharsets.UTF_8)));
                writeClient.sendRecord(3,
                    null,
                    ByteBuffer.wrap(("value3_" + i).getBytes(StandardCharsets.UTF_8)));
            }

            writeClient.finishUpload();

            // Read from server group 1
            for (ServerDetail serverDetail: group1) {
                List<RecordKeyValuePair> records;
                int port = ServerHostAndPort.fromString(serverDetail.getConnectionString()).getPort();

                records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 0, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), 1);
                Assert.assertEquals(records.get(0).getKey(), null);
                Assert.assertEquals(records.get(0).getValue(), new byte[0]);

                records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 2, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), numRecords);
                for (int i = 0; i < numRecords; i ++) {
                    Assert.assertEquals(records.get(i).getKey(), null);
                    Assert.assertEquals(new String(records.get(i).getValue(), StandardCharsets.UTF_8), "value2_" + i);
                }
            }

            // Read from server group 2
            for (ServerDetail serverDetail: group2) {
                List<RecordKeyValuePair> records;
                int port = ServerHostAndPort.fromString(serverDetail.getConnectionString()).getPort();

                records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 1, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), 2);
                Assert.assertEquals(records.get(0).getKey(), null);
                Assert.assertEquals(records.get(0).getValue(), new byte[0]);
                Assert.assertEquals(records.get(1).getKey(), null);
                Assert.assertEquals(records.get(1).getValue(), new byte[0]);

                records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 3, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
                Assert.assertEquals(records.size(), numRecords);
                for (int i = 0; i < numRecords; i ++) {
                    Assert.assertEquals(records.get(i).getKey(), null);
                    Assert.assertEquals(new String(records.get(i).getValue(), StandardCharsets.UTF_8), "value3_" + i);
                }
            }
        } finally {
            testServers.forEach(t->t.shutdown());
        }
    }

    @Test(dataProvider = "data-provider")
    public void writeAndReadRecords_twoServersPerPartition(int numTestServers, boolean finishUploadAck, boolean usePooledConnection, int writeQueueSize) {
        if (numTestServers <= 1) {
            return;
        }

        List<TestStreamServer> testServers = new ArrayList<>();
        for (int i = 0; i < numTestServers; i++) {
            testServers.add(TestStreamServer.createRunningServer());
        }

        List<ServerDetail> serverDetails = testServers.stream()
            .map(t->new ServerDetail(t.getServerId(), t.getRunningVersion(), t.getShuffleConnectionString()))
            .collect(Collectors.toList());

        List<ServerDetail> group1 = new ArrayList<>();
        List<ServerDetail> group2 = new ArrayList<>();
        for (int i = 0; i < serverDetails.size(); i++) {
            if (i % 2 == 0) {
                group1.add(serverDetails.get(i));
            } else {
                group2.add(serverDetails.get(i));
            }
        }

        int numWriteThreads = 2;

        int numMaps = 1;
        AppTaskAttemptId appTaskAttemptId = new AppTaskAttemptId("app1", "exec1", 1, 2, 0L);

        int numServersPerPartition = 2;
        int numRecords = 100000;

        List<RecordKeyValuePair> partition2WriteRecords = new ArrayList<>();
        List<RecordKeyValuePair> partition3WriteRecords = new ArrayList<>();

        try (MultiServerAsyncWriteClient writeClient = new MultiServerAsyncWriteClient(
            Arrays.asList(new ServerReplicationGroup(group1), new ServerReplicationGroup(group2)),
            numServersPerPartition,
            TestConstants.NETWORK_TIMEOUT,
            TestConstants.NETWORK_TIMEOUT,
            null,
            finishUploadAck,
            usePooledConnection,
            writeQueueSize,
            numWriteThreads,
            "user1",
            appTaskAttemptId.getAppId(),
            appTaskAttemptId.getAppAttempt(),
            new ShuffleWriteConfig()
        )) {
            writeClient.connect();
            writeClient.startUpload(appTaskAttemptId, numMaps, 20);

            writeClient.sendRecord(0, null, null);

            writeClient.sendRecord(1,
                ByteBuffer.wrap(new byte[0]),
                ByteBuffer.wrap(new byte[0]));
            writeClient.sendRecord(1,
                ByteBuffer.wrap(new byte[0]),
                ByteBuffer.wrap(new byte[0]));

            for (int i = 0; i < numRecords; i ++) {
                RecordKeyValuePair partition2Record = new RecordKeyValuePair(
                    null,
                    ("value2_" + i).getBytes(StandardCharsets.UTF_8),
                    appTaskAttemptId.getTaskAttemptId());
                writeClient.sendRecord(2,
                    null,
                    ByteBuffer.wrap(partition2Record.getValue()));
                partition2WriteRecords.add(partition2Record);

                RecordKeyValuePair partition3Record = new RecordKeyValuePair(
                    null,
                    ("value33333333333333333333333333333333_" + i).getBytes(StandardCharsets.UTF_8),
                    appTaskAttemptId.getTaskAttemptId());
                writeClient.sendRecord(3,
                    null,
                    ByteBuffer.wrap(partition3Record.getValue()));
                partition3WriteRecords.add(partition3Record);
            }

            writeClient.finishUpload();

            List<RecordKeyValuePair> readRecords = new ArrayList<>();

            // Read partition 0 from first sever in replication group 1
            List<RecordKeyValuePair> records;
            int port = ServerHostAndPort.fromString(group1.get(0).getConnectionString()).getPort();
            records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 0, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
            readRecords.addAll(records);

            // Read partition 0 from last server in replication group 2
            port = ServerHostAndPort.fromString(group2.get(group2.size() - 1).getConnectionString()).getPort();
            records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 0, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
            readRecords.addAll(records);

            // Verify records for partition 0
            Assert.assertEquals(readRecords.size(), 1);
            Assert.assertEquals(readRecords.get(0).getKey(), null);
            Assert.assertEquals(readRecords.get(0).getValue(), new byte[0]);

            readRecords = new ArrayList<>();

            // Read partition 1 from first sever in replication group 1
            port = ServerHostAndPort.fromString(group1.get(0).getConnectionString()).getPort();
            records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 1, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
            readRecords.addAll(records);

            // Read partition 1 from last server in replication group 2
            port = ServerHostAndPort.fromString(group2.get(group2.size() - 1).getConnectionString()).getPort();
            records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 1, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
            readRecords.addAll(records);

            // Verify records for partition 1
            Assert.assertEquals(readRecords.size(), 2);
            Assert.assertEquals(readRecords.get(0).getKey(), null);
            Assert.assertEquals(readRecords.get(0).getValue(), new byte[0]);
            Assert.assertEquals(readRecords.get(1).getKey(), null);
            Assert.assertEquals(readRecords.get(1).getValue(), new byte[0]);

            readRecords = new ArrayList<>();

            // Read partition 2 from first sever in replication group 1
            port = ServerHostAndPort.fromString(group1.get(0).getConnectionString()).getPort();
            records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 2, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
            readRecords.addAll(records);

            // Read partition 2 from last server in replication group 2
            port = ServerHostAndPort.fromString(group2.get(group2.size() - 1).getConnectionString()).getPort();
            records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 2, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
            readRecords.addAll(records);

            // Verify records for partition 2
            Assert.assertEquals(readRecords.size(), numRecords);
            Assert.assertEquals(readRecords.size(), partition2WriteRecords.size());
            Assert.assertEquals(new HashSet<>(readRecords), new HashSet<>(partition2WriteRecords));

            readRecords = new ArrayList<>();

            // Read partition 3 from first sever in replication group 1
            port = ServerHostAndPort.fromString(group1.get(0).getConnectionString()).getPort();
            records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 3, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
            readRecords.addAll(records);

            // Read partition 3 from last server in replication group 2
            port = ServerHostAndPort.fromString(group2.get(group2.size() - 1).getConnectionString()).getPort();
            records = StreamServerTestUtils.readAllRecords2(port, appTaskAttemptId.getAppShuffleId(), 3, Arrays.asList(appTaskAttemptId.getTaskAttemptId()));
            readRecords.addAll(records);

            // Verify records for partition 3
            Assert.assertEquals(readRecords.size(), numRecords);
            Assert.assertEquals(readRecords.size(), partition3WriteRecords.size());
            Assert.assertEquals(new HashSet<>(readRecords), new HashSet<>(partition3WriteRecords));
        } finally {
            testServers.forEach(t->t.shutdown());
        }
    }
}
