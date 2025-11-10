# IoT Data Pipeline (MVP)

> Continuous ingestion, streaming aggregation, durable storage, and secure querying of IoT sensor data.

---

## Architecture Overview

**System Diagram:**


> ![Architecture Diagram](/docs/architecture.png "Architecture diagram")


## Local Deployment

### Prerequisites

- Docker
- Java 17 + Maven

### Startup Sequence

```bash
# Build all the maven submodules
mvn clean package

# Start entire infrastructure + services
docker compose -f docker/docker-compose.yml up -d

# Shutdown everything
docker compose -f docker/docker-compose.yml down -v
```

## Local Observability and Resources

- [KafkaUI](http://localhost:8085) : http://localhost:8085
- [Flink UI](http://localhost:8081) : http://localhost:8081
- [Cassandra Web UI](http://localhost:8087) : http://localhost:8087
- [Keycloak](http://localhost:8088) : http://localhost:8088 (admin / admin)
- [Query API](http://localhost:8083/query/{deviceId}/1m) : http://localhost:8083/query/{deviceId}/1m

## System flow

- After all the infra and services are up, the device emulator starts producing measurements at a default rate of 50 devices per second
  - The measurements are sent to the Ingress Service REST api
- Ingress service publishes the received measurements to the iot.readings Kafka topic
- The Flink job to aggregate data is also submitted at startup, and it consumes the events from Kafka and aggregates the measurements in a tumbling window of 1 minute
  - After the 1m window closes (+ potential out of order and late arrivals are accounted) Flink dumps the aggregations to the respective Cassandra tables
- 3 tables are populated in Cassandra, 1m, 1h and all time aggregations
- Query service REST endpoints can be used to query
  - Authentication is needed: Keycloak starts up an imports a realm in which the user "client1" is already set up and can ask for tokens
  ```bash
  TOKEN=$(curl -s -X POST 'http://localhost:8088/realms/iot/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=query-service' \
  -d 'username=client1' \
  -d 'password=temp' | jq -r .access_token)
  curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8083/query/5e445786-ac04-425b-8a12-d437f4cc704e/1m```

## Architectural Justification

- To satisfy the Scalability, Performance and Durability non functionals the decision was to go with the Kafka + Flink + Cassandra infrastructure
  - Scalability: Kafka partitions + Flink parallelism + Cassandra shards - scale horizontally across all stages; Spring services being stateless can be scaled horizontally when in need
  - Durability: Kafka retains the raw stream; Cassandra persists aggregates with TTL policies for cost control
  - Continuous processing: Flink keeps aggregates fresh in near-real time with event-time semantics, exactly-once guarantees, and resumable state
  - Future-proofing: When we will need richer windows or group aggregations we can add Flink operators and new Cassandra tables without touching device senders

- Kafka (ingress + durability + fan-out)
  - Scales with devices: being a distributed partitioned log it lets us shard by deviceId and add partitions as traffic grows
  - Durable & replayable: acts as a record for raw telemetry; consumers can reprocess from offsets for backfills or new aggregations (adjustable TTL)
  - Smooths bursts: built-in backpressure and retention decouples producers from downstream outages or spikes
  - Battle tested, production ready: exactly-once semantics with Flink, DLQs for poison messages, mature tooling

- Flink (continuous, stateful stream processing)
  - Event-time correctness: Tumbling windows (1m/1h) with watermarks handle late/out-of-order device data accurately
  - State at scale: RocksDB/heap state with checkpoints gives consistent, recoverable aggregation state at high throughput
  - Exactly-once end-to-end: Kafka source + checkpointing + idempotent sinks yield correct aggregates despite failures
  - Flexible topology: Clean separation of 1m/1h windows and all-time rollups; easy to add different streams later without changing producers

- Cassandra (write-path performance + time-series at scale)
  - Write-optimized + linearly scalable: fast, predictable writes across a cluster; scale by adding nodes, favors high write throughput due to LSM tree backed index and storage
  - Time-series friendly: bucketing (bucket_month) + TWCS + TTLs allow efficient compaction and bounded partitions for hot data
  - Query-aligned schemas: per-window aggregates keyed by (device_id, bucket_month, window_start|hour_start) deliver fast reads for “last 1m/1h/all-time”
  - Always-on availability: masterless architecture tolerates node failures with no single point of failure; ideal for 24/7 ingestion

## Future work

- Extensive testing, unit tests, integration tests and smoke tests for the entire e2e flow. This will make our lives easier when refactoring or adding new logic
- Near future new logic: add groups for logical device grouping + Client UI for measurements visualization
- Observability: Prometheus + Grafana, use exporters for the infrastructure + Spring actuator for services to aggregate metrics
  - build a master dashboard to keep an eye on the system as a whole + SLAs and set alerts 
  - per infra component dashboard 
- Security:
  - mTLS for device authentication for the Ingress service communication
  - hardened Keycloak, TLS required realm
- Data retention strategy: Kafka and Cassandra TTLs and Flink storage

## Cloud deployment on AWS (managed-first vs. self-hosted)
- We have a budget / time to market tradeoff
  - we can consider MSK (managed Kafka) OR Kinesis (locks us in aws) + managed Flink + Amazon Keyspaces (managed Cassandra) + a small managed K8s cluster for the Java services (autoscaling benefits) and Keycloak on EKS behind ALB + ACM TLS
    - together with the usual additionals, IAM, api gateway and ELBs, Secrets Manager / ParamStore, DNS, Amazon Managed Prometheus and Managed Grafana etc
  - middle ground to cut costs: we invest more time in self hosting, and deploy resources in EKS. Flink K8s operator, Cassandra K8s operator, Kafka K8s operator, for infra deployment
    - More control, significantly more operational burden (patching, scaling, failure handling)
    - Full control over versions/tuning, no vendor lock-in beyond AWS infra
  - at the other extreme: we can go even further and we can host the Kubernetes cluster on EC2 instances before we deploy the rest
- Other things to consider: 
  - setting up the VPC - private subnets for the data plane, public subnet for the ingress/ALB
  - Container registry + image scanning 
  - Setting up CI/CD pipelines to automate building and deployment, ArgoCD for deployments "from inside the cluster"
  - IaC, Terraform, the entire infrastructure should be in code under version control (VPC, EKS, data plane, IAM, ACM (certificates), DNS (Route53)), everything reproducible in an autiomated fashion
  - Of course MRs will trigger the CI/CD pipelines, the pipelines will block on failed tests, failed deps and image scans, and smoke tests will bloke upper envs push
  - Ideally 3 environments: Dev, Staging and Prod
  - RBAC access to the above, ideally almost nobody has access to Prod aside from a few key people. Dev accessed by developers, Staging accessed by team lead, Prod accessed by release manager / engineering manager
  - Logs and Traces, CloudWatch is an option, if it becomes a cost burden we can transition to ELK 