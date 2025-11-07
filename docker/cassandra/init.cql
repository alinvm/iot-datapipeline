CREATE KEYSPACE IF NOT EXISTS iot
    WITH replication = {'class':'SimpleStrategy','replication_factor':1};

-- Month bucket: yyyy*100 + mm
CREATE TABLE IF NOT EXISTS iot.agg_device_1m (
    device_id     uuid,
    bucket_month  int,
    window_start  timestamp,      -- aligned minute start (UTC)
    count         bigint,
    sum           double,
    min           double,
    max           double,
    tdigest       blob,
    PRIMARY KEY ((device_id, bucket_month), window_start)
) WITH CLUSTERING ORDER BY (window_start DESC)
   AND compaction = {
    'class': 'TimeWindowCompactionStrategy',
    'compaction_window_unit': 'DAYS',
    'compaction_window_size': '1'
    }
   AND default_time_to_live = 2592000;   -- 30 days

CREATE TABLE IF NOT EXISTS iot.agg_device_1h (
    device_id     uuid,
    bucket_month  int,
    hour_start    timestamp,      -- aligned hour start (UTC)
    count         bigint,
    sum           double,
    min           double,
    max           double,
    tdigest       blob,
    PRIMARY KEY ((device_id, bucket_month), hour_start)
) WITH CLUSTERING ORDER BY (hour_start DESC)
   AND compaction = {
    'class': 'TimeWindowCompactionStrategy',
    'compaction_window_unit': 'DAYS',
    'compaction_window_size': '1'
    }
   AND default_time_to_live = 31536000;  -- ~12 months

CREATE TABLE IF NOT EXISTS iot.agg_device_alltime (
    device_id uuid PRIMARY KEY,
    count     bigint,
    sum       double,
    min       double,
    max       double,
    tdigest   blob
);
