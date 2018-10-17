docker run --name my-prometheus \
    --mount type=bind,source=/home/wlip/Documents/Aeron/aeron-prometheus-stats/examples/prometheus.yml,destination=/etc/prometheus/prometheus.yml \
    --network host \
    -p 9090:9090 prom/prometheus


docker run \
  -p 3000:3000 \
  --name=grafana \
  -v /home/wlip/Documents/Aeron/aeron-prometheus-stats/examples/grafana-storage:/var/lib/grafana \
  --network host \
  --user 1000 \
  grafana/grafana