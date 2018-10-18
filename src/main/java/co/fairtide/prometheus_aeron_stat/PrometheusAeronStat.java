/*
 * Copyright 2018 Fairtide Pte. Ltd.
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
 */
package co.fairtide.prometheus_aeron_stat;

import io.aeron.CommonContext;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.status.CountersReader;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.CncFileDescriptor.*;

import static io.aeron.driver.status.PublisherLimit.PUBLISHER_LIMIT_TYPE_ID;
import static io.aeron.driver.status.SystemCounterDescriptor.SYSTEM_COUNTER_TYPE_ID;
import static io.aeron.driver.status.SubscriberPos.SUBSCRIBER_POSITION_TYPE_ID;
import static io.aeron.driver.status.ClientHeartbeatStatus.CLIENT_HEARTBEAT_TYPE_ID;

public class PrometheusAeronStat {

    private static final String DELAY = "delay";
    private static final String PROMETHUES_EXPORTER_PORT = "port";
    private static final String HELP_STR = "help";
    private static final String GET_ARCHIVE_STR = "archive";
    private static final String HELP_MSG = "[-aeron.dir=<directory containing CnC file>] PrometheusAeronStat" +
            "\tUsage: [-port <port for prometheus server COMPULSORY>]" +
            "\t[-delay <seconds between updates>]%n" +
            "\t[-archive]%n" +
            "\t";


    private CountersReader counters;
    private HashMap<String, Counter> prometheusCounterMap;
    private int numChannelPublishingCounter;
    private int numSubscriberCounter;
    private int numClientCounter;

    private Gauge numChannelPublishingGauge;
    private Gauge numSubscriberGauge;
    private Gauge numClientGauge;


    public PrometheusAeronStat(CountersReader counters) {
        this.counters = counters;
        this.prometheusCounterMap = new HashMap<>();
        this.numChannelPublishingGauge = Gauge.build().name("num_channel").help("num_channel").register();
        this.numSubscriberGauge = Gauge.build().name("num_subscribers").help("num_channel").register();
        this.numClientGauge = Gauge.build().name("num_clients").help("num_channel").register();
    }

    public static CountersReader mapCounters() {
        final File cncFile = CommonContext.newDefaultCncFile();
        System.out.println("Command n Control file path is: " + cncFile);

        final MappedByteBuffer cncByteBuffer = IoUtil.mapExistingFile(cncFile, "cnc");
        final DirectBuffer cncMetaData = createMetaDataBuffer(cncByteBuffer);
        final int cncVersion = cncMetaData.getInt(cncVersionOffset(0));

        if (CNC_VERSION != cncVersion) {
            throw new IllegalStateException(
                    "Aeron CnC version does not match: version=" + cncVersion + " required=" + CNC_VERSION);
        }

        return new CountersReader(
                createCountersMetaDataBuffer(cncByteBuffer, cncMetaData),
                createCountersValuesBuffer(cncByteBuffer, cncMetaData),
                StandardCharsets.US_ASCII);
    }

    public static void main(final String[] args) throws Exception {
        long delayMs = 5000L;
        int portNumber;
        boolean getArchive;

        Options options = new Options();
        options.addOption(DELAY, true, "Frequency to query for statistics in milliseconds");
        options.addOption(PROMETHUES_EXPORTER_PORT, true, "Port to expose prometheus statistics");
        options.addOption(HELP_STR, false, "Print out example usage.");
        options.addOption(GET_ARCHIVE_STR, false, "Get archive logs.");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(HELP_STR) || !cmd.hasOption(PROMETHUES_EXPORTER_PORT)) {
            System.out.println(HELP_MSG);
            System.exit(0);
        }
        if (cmd.hasOption(DELAY)){
            delayMs = Long.parseLong(cmd.getOptionValue(DELAY));
        }

        getArchive = cmd.hasOption(GET_ARCHIVE_STR);
        portNumber = Integer.parseInt(cmd.getOptionValue(PROMETHUES_EXPORTER_PORT));

        HTTPServer prometheusServer = new HTTPServer(portNumber);

        final PrometheusAeronStat prometheusAeronStat = new PrometheusAeronStat(mapCounters());
        final AtomicBoolean running = new AtomicBoolean(true);
        SigInt.register(() -> running.set(false));

        if (!getArchive) {
            while (running.get()) {
                prometheusAeronStat.updatePrometheus();
                Thread.sleep(delayMs);
            }
        } else {

            final AeronArchive.Context archiveCtx = new AeronArchive.Context()
                    .controlResponseStreamId(AeronArchive.Configuration.controlResponseStreamId() + 1);
            try (AeronArchive archive = AeronArchive.connect(archiveCtx)) {
                while (running.get()) {
                    prometheusAeronStat.updatePrometheus();
                    prometheusAeronStat.printArchiveLogs(archive);
                    Thread.sleep(delayMs);
                }
            } catch (Exception e) {
                System.out.println(e);
            }
        }
        prometheusServer.stop();
    }


    public void updatePrometheus() {
        this.clearOldCounter();
        counters.forEach(
                (counterId, typeId, keyBuffer, label) ->
                {
                    switch (typeId) {
                        case SYSTEM_COUNTER_TYPE_ID:
                            this.updateSystemCounter(counters, counterId, typeId, label);
                            break;
                        case PUBLISHER_LIMIT_TYPE_ID:
                            this.numChannelPublishingCounter++;
                            break;
                        case SUBSCRIBER_POSITION_TYPE_ID:
                            this.numSubscriberCounter++;
                            break;
                        case CLIENT_HEARTBEAT_TYPE_ID:
                            this.numClientCounter++;
                            break;
                        default:
                            break;
                    }
                });
        this.numChannelPublishingGauge.set(this.numChannelPublishingCounter);
        this.numSubscriberGauge.set(this.numSubscriberCounter);
        this.numClientGauge.set(this.numClientCounter);
    }

    public void printArchiveLogs(final AeronArchive archive) {
        final RecordingDescriptorConsumer consumer =
                (controlSessionId,
                 correlationId,
                 recordingId,
                 startTimestamp,
                 stopTimestamp,
                 startPosition,
                 stopPosition,
                 initialTermId,
                 segmentFileLength,
                 termBufferLength,
                 mtuLength,
                 sessionId,
                 streamId,
                 strippedChannel,
                 originalChannel,
                 sourceIdentity) -> {
                    System.out.format("[recordingId]: %d, " +
                                    "[Timestamp]: [%d, %d], [startPosition]: %d, [stopPosition]: %d, [initialTermId]: %d, " +
                                    "[segmentFileLength]: %d, [sessionId]: %d, [streamId]: %d, [originalChannel]: %s, " +
                                    "[SourceIdentity]: %s\n"
                            , recordingId, startTimestamp, stopTimestamp,
                            startPosition, stopPosition, initialTermId,
                            segmentFileLength, sessionId, streamId,
                            originalChannel, sourceIdentity);
                };
        //Print 100k recordings can be parameterize
        final long fromRecordingId = 0L;
        final int recordCount = 1000;
        final int foundCount = archive.listRecordings(fromRecordingId, recordCount, consumer);
        System.out.println("Number of recording is: " + foundCount);
    }


    public void updateSystemCounter(CountersReader cr, int counterId, int typeId, String label) {
        String smallLabel = label.replaceAll(" ", "_").toLowerCase();
        Counter prometheusCounter;
        if (prometheusCounterMap.get(smallLabel) == null) {
            prometheusCounter = Counter.build().name(smallLabel).help(Integer.toString(typeId)).register();
            prometheusCounterMap.put(smallLabel, prometheusCounter);
        } else {
            prometheusCounter = prometheusCounterMap.get(smallLabel);
        }
        prometheusCounterMap.put(smallLabel, prometheusCounter);
        final long currentValue = cr.getCounterValue(counterId);
        final double prevVal = prometheusCounter.get();
        final double diff = currentValue - prevVal;
        prometheusCounter.inc(diff);
    }

    public void clearOldCounter() {
        this.numChannelPublishingCounter = 0;
        this.numSubscriberCounter = 0;
        this.numClientCounter = 0;
    }
}
