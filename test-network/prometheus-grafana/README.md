# Description

This sample provides an environment to display and capture metrics from the test-network in real time. It consists of a docker-compose file that starts a prometheus and grafana server setup configured to collect and display metrics for the test network.

# Requirements

This sample is meant to be used on **linux** in order to fully benefit from its capabilities. However, it can be used and adapted on other systems with some additional work and tweaks.

You will need to have installed **docker-compose with version 1.29 or above**.

# How to use

1. Bring up the test-network 
2. Bring up the Prometheus/Grafana network --> **docker-compose up -d**
3. Log in: type “< address >:3000” on your web browser -> username=“admin”, password=“admin” 
4. Browse dashboard and analyze results 
Extras: add new queries, modify dashboard & add relevant changes to main repo --> extract json and it to "grafana/dashboards/hlf-performances.json"

# Docker Compose

Brings up
   - a prometheus sever (port 9090) --> pulls metrics from peers, orderer, system(node exporter) and containers(cadvisor)
   - grafana sever (port 3000) --> collects and display data from prometheus
   - node exporter (port 9100) --> exposes systems metrics
   - cadvisor (port 8080) --> exposes docker conteners metrics

# Prometheus "confgaration file"

**Prometheus.yml**

Fabric metrics targets:
   - peer0.org1.example.com:9444
   - peer0.org2.example.com:9445
   - orderer.example.com:9443

System and docker metrics targets:
   - cadvisor:8080
   - node-exporter:9100

Check the state of the connections with targets on http://localhost:9090/targets.


# Sources

Prometheus docs: https://prometheus.io/docs/introduction/overview/
Grafana docs: https://grafana.com/docs/

