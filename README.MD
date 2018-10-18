# Aeron Stats Prometheus Exporter  

Prometheus exporter for [Aeron](https://github.com/real-logic/aeron) metrics.

Data is scraped by [prometheus](https://prometheus.io).


## Installation
```$xslt
./gradlew fatJar
```
## Running Locally

Firstly we will need to run a [aeron media driver](https://github.com/real-logic/aeron).

To run the stats exporter simply run the following while media driver is running. Delay is optional defaults to 5 second. Port is compulsory argument.
```$xslt
java -cp build/libs/aeron-prometheus-stats-all-1.0-SNAPSHOT.jar -Daeron.dir=<same_directory_as_media_driver> co.fairtide.prometheus_aeron_stat.PrometheusAeronStat -port 1234 -delay <in_ms>
```
You can also run with the archive option (Only works with ArchivingMediaDriver). This prints out to console the archive details.
```$xslt
java -cp build/libs/aeron-prometheus-stats-all-1.0-SNAPSHOT.jar -Daeron.dir=<same_directory_as_media_driver> co.fairtide.prometheus_aeron_stat.PrometheusAeronStat -port 1234 -delay <in_ms> -archive
```
To see the options you can run. 
```$xslt
java -cp build/libs/aeron-prometheus-stats-all-1.0-SNAPSHOT.jar  co.fairtide.prometheus_aeron_stat.PrometheusAeronStat -help
```
### Docker:

1.  Run a [aeron media driver](https://github.com/real-logic/aeron) first.


2. Build the docker image

        docker build -t prometheusaeron .
        
3. Run the docker image. Example configuration. To change any other command 

        PROMETHEUS_PORT=1234 AERON_DIR=/dev/shm/aeron-asd docker-compose -f docker-compose.yml up

Now your metrics are exposed through http://host:1234/metrics.

## Docker Configuration

Uses environment variables for configuration.
Settings:

Environment variable|default|description
--------------------|-------|------------
PROMETHEUS_PORT | No Default. Compulsory Argument| Port for prometheus to scrape.
AERON_DIR | No Default. Compulsory Argument | Check link for [details](https://github.com/real-logic/aeron)
 

For delay and archive settings please manually change the run command in the docker compose file. Delay is set to 5 seconds in compose file. 
And archive is not being set. 

Example of changing delay to 10 seconds and to print logs of ArchivingMediaDriver

```$xslt
        command: >
            bash -c "java -cp /opt/aeron-prometheus-stats/lib/aeron-prometheus-stats-all-1.0-SNAPSHOT.jar\
             -Daeron.dir=/dev/shm/aeron co.fairtide.prometheus_aeron_stat.PrometheusAeronStat -port 50051 -delay 10000 -archive"
```


## Metrics

Two main type of metrics are exposed 

1. **System Counters**

Counters of significant events observed in the system such as errors, counts, rates, and hints that further investigation should be taken.
Aeron link -> [System counters](https://github.com/real-logic/aeron/wiki/Monitoring-and-Debugging) 

2. **Derived stats from Stream Positions**

num_channel - Number of channels that is going through media driver

num_subscribers - Number of subcribers on media driver

num_clients - Number of clients(subcriber and publisher) on media driver


### System Counters - Counter

The name of the stats almost directly come from aeron ([link](https://github.com/real-logic/aeron/blob/master/aeron-driver/src/main/java/io/aeron/driver/status/SystemCounterDescriptor.java)) It is renamed to suit prometheus conventions.

Command used to rename.
```$xslt
label.replaceAll(" ", "_").toLowerCase();
```

metric | description
-------| ------------
|bytes_sent | Total number of open channels|
|bytes_received | Total number of open connections|
|failed_offers_to_receiverProxy | Total number of message consumers|
|failed_offers_to_senderProxy | Total number of queues in use|
|failed_offers_to_driverconductorproxy | Total number of exchanges in use|
|naks_sent | Total number ready and unacknowledged messages in cluster.|
|naks_received | NAKs received.|
|status_messages_sent | Status Messages sent.|
|status_messages_received | Status Messages received|
|heartbeats_sent | Heartbeats sent|
|heartbeats_received | Heartbeats received|
|retransmits_sent | Retransmits sent|
|flow_control_under_runs | Flow control under runs|
|flow_control_over_runs | Flow control over runs.|
|invalid_packets | Invalid packets.|
|errors | Errors.|
|short_sends | Short sends|
|failed_attempts_to_free_log_buffers | Failed attempts to free log buffers|
|sender_flow_control_limits_applied | Sender flow control limits applied|
|unblocked_publications | Unblocked Publications.|
|unblocked_control_commands | Unblocked Control Commands.|
|possible_ttl_asymmetry | Possible TTL Asymmetry.|
|controllableidlestrategy_status | ControllableIdleStrategy status|
|Loss gap fills | Loss gap fills|

### Derived stats from Stream Positions - Gauge

Side note - running Arhive media driver will run a publisher on a channel.

metric | description
-------| ------------
|num_channel|number of channels in use|
|num_subscribers|received bytes|
|num_clients|received packets|


### Example with prometheus and grafphana

**1. Get the docker images**

```$xslt
docker build -t prometheusaeron .
```
```$xslt
docker pull prom/prometheus
```
```$xslt
docker pull grafana/grafana
```
**2. Run Aeron Media Driver, Prometheus, Aeron-prometheus-stats and grafana**

**Aeron Media Driver**

Run aeron media drivers, publisher and subcribers. [Link](https://github.com/real-logic/aeron)

**Aeron-prometheus-stats**

```$xslt
 PROMETHEUS_PORT=1234 DAERON_DIR=/dev/shm/aeron-asd docker-compose -f docker-compose.yml up
```

**Prometheus** - [Link](https://docs.docker.com/config/thirdparty/prometheus/#configure-and-run-prometheus)

```$xslt
docker run --name my-prometheus \
    --mount type=bind,source=<PATH_TO_REPO>/examples/prometheus.yml,destination=/etc/prometheus/prometheus.yml \
    --network host \
    -p 9090:9090 prom/prometheus
```

**Grafana** - [Link](http://docs.grafana.org/installation/docker/)

```$xslt
docker run \
  -p 3000:3000 \
  --name=grafana \
  -v <PATH_TO_REPO>/examples/grafana-storage:/var/lib/grafana \
  --network host \
  --user 1000 \
  grafana/grafana
```

Navigate to [localhost:3000](http://localhost:3000)

User:admin

pass:admin

Then navigate to aeron-prometheus-stats dashboard.

![alt text](https://raw.githubusercontent.com/fairtide/aeron-prometheus-stat/develop/examples/Screenshots/GrafanaExample.png)


## Licence (See LICENSE file for full license)
Copyright 2018 Fairtide Pte. Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
