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

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.CncFileDescriptor.*;



public class PrometheusAeronStat {
    /**
     * The delay in seconds between each update.
     */
    private static final String DELAY = "delay";
    private static final String PROMETHUES_EXPORTER_PORT = "port";

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
        System.out.println("Command `n Control file " + cncFile);

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
        long delayMs = 1000L;
        int portNumber = -1;

        if (0 != args.length) {
            checkForHelp(args);
            for (final String arg : args) {
                final int equalsIndex = arg.indexOf('=');
                if (-1 == equalsIndex) {
                    System.out.println("Arguments must be in name=pattern format: Invalid '" + arg + "'");
                    return;
                }
                final String argName = arg.substring(0, equalsIndex);
                final String argValue = arg.substring(equalsIndex + 1);

                switch (argName) {
                    case DELAY:
                        delayMs = Long.parseLong(argValue) * 1000L;
                        break;
                    case PROMETHUES_EXPORTER_PORT:
                        portNumber = Integer.parseInt(argValue);
                        break;
                    default:
                        System.out.println("Unrecognised argument: '" + arg + "'");
//                        return;
                }
            }
        }
        if (portNumber == -1) {
            System.out.println("Port to expose prometheues statistics is a compulsory argument");
            System.out.println(
                    "Usage: [port=<port for prometheus server COMPULSORY>]" +
                            "\t[delay=<seconds between updates>]%n" +
                            "\t[-Daeron.dir=<directory containing CnC file>] ");
            System.exit(0);
            return;
        }

        HTTPServer prometheusServer = new HTTPServer(portNumber);

        final PrometheusAeronStat prometheusAeronStat = new PrometheusAeronStat(mapCounters());
        final AtomicBoolean running = new AtomicBoolean(true);
        SigInt.register(() -> running.set(false));

        while (running.get()) {
            prometheusAeronStat.updatePrometheus();
            Thread.sleep(delayMs);
        }
// TODO Add archive logs when martin thompson fixes the bug https://github.com/real-logic/aeron/issues/565
//        try (AeronArchive archive = AeronArchive.connect()) {
//            while (running.get()) {
//                prometheusAeronStat.updatePrometheus();
//                prometheusAeronStat.printArchiveLogs(archive);
//                Thread.sleep(delayMs);
//            }
//
//        } catch (Exception e) {
//            System.out.println(e);
//        }
        prometheusServer.stop();

    }


    public void updatePrometheus() {
        this.clearOldCounter();
        counters.forEach(
                (counterId, typeId, keyBuffer, label) ->
                {
                    switch (typeId) {
                        case 0:
                            this.updateSystemCounter(counters, counterId, typeId, label);
                            break;
                        case 1:
                            this.numChannelPublishingCounter++;
                            break;
                        case 2:
                            break;
                        case 3:
                            break;
                        case 4:
                            this.numSubscriberCounter++;
                            break;
                        case 5:
                            break;
                        case 6:
                            break;
                        case 7:
                            break;
                        case 9:
                            break;
                        case 10:
                            break;
                        case 11:
                            System.out.println(label);
                            this.numClientCounter++;
                            break;
                        default:
                            break;
                    }
                });
        System.out.println("Lol: " + this.numChannelPublishingCounter);
        System.out.println("Lol: " + this.numSubscriberCounter);
        System.out.println("Lol: " + this.numClientCounter);

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
                                    "[segmentFileLength]: %d, [sessionId]: %d, [streamId]: %d, [originalChannel]: %s" +
                                    "[SourceIdentity]: %s\n"
                            , recordingId, startTimestamp, stopTimestamp,
                            startPosition, stopPosition, initialTermId,
                            segmentFileLength, sessionId, streamId,
                            originalChannel, sourceIdentity);
                };
        //Print 100k recordings can be parameterize
        final long fromRecordingId = 0L;
        final int recordCount = 100000;
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


    private static void checkForHelp(final String[] args) {
        for (final String arg : args) {
            if ("-?".equals(arg) || "-h".equals(arg) || "-help".equals(arg)) {
                System.out.println(
                        "Usage: [port=<port for prometheus server COMPULSORY>]" +
                                "\t[delay=<seconds between updates>]%n" +
                                "\t[-Daeron.dir=<directory containing CnC file>] ");
                System.exit(0);
            }
        }
    }
}
