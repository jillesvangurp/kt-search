#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: create-ds-and-template.sh [options]

Options:
  --es-url URL                         Elasticsearch base URL (default: http://localhost:9200)
  --prefix NAME                        Data stream/template prefix (alias for --data-stream-name)
  --data-stream-name NAME              Data stream name (default: applogs)
  --manage-data-stream-and-templates   true|false (default: true)
  --configure-ilm                      true|false (default: true)
  --hot-roll-over-gb N                 ILM hot rollover primary shard size in GB
  --hot-max-age VALUE                  ILM hot max age (e.g. 1d)
  --number-of-replicas N               index.number_of_replicas
  --number-of-shards N                 index.number_of_shards
  --warm-min-age-days N                Warm phase min age in days (e.g. 3 -> 3d)
  --delete-min-age-days N              Delete phase min age in days (e.g. 30 -> 30d)
  --warm-shrink-shards N               Warm phase shrink target shards
  --warm-segments N                    Warm phase forcemerge max segments
  --warm-min-age VALUE                 Warm phase min age (raw, e.g. 1d)
  --delete-min-age VALUE               Delete phase min age (raw, e.g. 14d)
  -h, --help                           Show this help

Environment aliases are supported for compatibility:
  dataStreamName, manageDataStreamAndTemplates, configureIlm,
  hotRollOverGb, hotMaxAge, numberOfReplicas, numberOfShards,
  warmMinAgeDays, deleteMinAgeDays, warmShrinkShards, warmSegments.
USAGE
}

truthy() {
  case "$1" in
    [Tt][Rr][Uu][Ee]|1|[Yy][Ee][Ss]|[Yy]|[Oo][Nn]) return 0 ;;
    *) return 1 ;;
  esac
}

# defaults and compatibility aliases
ES_URL="${ES_URL:-http://localhost:9200}"
DATA_STREAM_NAME="${dataStreamName:-${DATA_STREAM_NAME:-${PREFIX:-applogs}}}"
MANAGE_DATA_STREAM_AND_TEMPLATES="${manageDataStreamAndTemplates:-${MANAGE_DATA_STREAM_AND_TEMPLATES:-true}}"
CONFIGURE_ILM="${configureIlm:-${CONFIGURE_ILM:-true}}"
HOT_ROLLOVER_GB="${hotRollOverGb:-${HOT_ROLLOVER_GB:-2}}"
HOT_MAX_AGE="${hotMaxAge:-${HOT_MAX_AGE:-1d}}"
NUMBER_OF_REPLICAS="${numberOfReplicas:-${NUMBER_OF_REPLICAS:-0}}"
NUMBER_OF_SHARDS="${numberOfShards:-${NUMBER_OF_SHARDS:-1}}"
WARM_MIN_AGE_DAYS="${warmMinAgeDays:-${WARM_MIN_AGE_DAYS:-1}}"
DELETE_MIN_AGE_DAYS="${deleteMinAgeDays:-${DELETE_MIN_AGE_DAYS:-14}}"
WARM_SHRINK_SHARDS="${warmShrinkShards:-${WARM_SHRINK_SHARDS:-1}}"
WARM_SEGMENTS="${warmSegments:-${WARM_SEGMENTS:-1}}"
WARM_MIN_AGE="${WARM_MIN_AGE:-${WARM_MIN_AGE_DAYS}d}"
DELETE_MIN_AGE="${DELETE_MIN_AGE:-${DELETE_MIN_AGE_DAYS}d}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --es-url) ES_URL="$2"; shift 2 ;;
    --prefix|--data-stream-name) DATA_STREAM_NAME="$2"; shift 2 ;;
    --manage-data-stream-and-templates) MANAGE_DATA_STREAM_AND_TEMPLATES="$2"; shift 2 ;;
    --configure-ilm) CONFIGURE_ILM="$2"; shift 2 ;;
    --hot-roll-over-gb) HOT_ROLLOVER_GB="$2"; shift 2 ;;
    --hot-max-age) HOT_MAX_AGE="$2"; shift 2 ;;
    --number-of-replicas) NUMBER_OF_REPLICAS="$2"; shift 2 ;;
    --number-of-shards) NUMBER_OF_SHARDS="$2"; shift 2 ;;
    --warm-min-age-days) WARM_MIN_AGE_DAYS="$2"; WARM_MIN_AGE="${2}d"; shift 2 ;;
    --delete-min-age-days) DELETE_MIN_AGE_DAYS="$2"; DELETE_MIN_AGE="${2}d"; shift 2 ;;
    --warm-shrink-shards) WARM_SHRINK_SHARDS="$2"; shift 2 ;;
    --warm-segments) WARM_SEGMENTS="$2"; shift 2 ;;
    --warm-min-age) WARM_MIN_AGE="$2"; shift 2 ;;
    --delete-min-age) DELETE_MIN_AGE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if ! truthy "$MANAGE_DATA_STREAM_AND_TEMPLATES"; then
  echo "manageDataStreamAndTemplates=false: skipping all provisioning work"
  exit 0
fi

PREFIX="$DATA_STREAM_NAME"
ILM_POLICY="${PREFIX}-ilm-policy"
TEMPLATE_SETTINGS="${PREFIX}-template-settings"
TEMPLATE_MAPPINGS="${PREFIX}-template-mappings"
INDEX_TEMPLATE="${PREFIX}-template"

curl_put() {
  curl -fsS -X PUT "$1" -H "Content-Type: application/json" -d "$2"
}

if truthy "$CONFIGURE_ILM"; then
  echo "Creating ILM policy $ILM_POLICY ..."
  curl_put "$ES_URL/_ilm/policy/$ILM_POLICY" "{
    \"policy\": {
      \"phases\": {
        \"hot\": {
          \"actions\": {
            \"rollover\": {
              \"max_primary_shard_size\": \"${HOT_ROLLOVER_GB}gb\",
              \"max_age\": \"$HOT_MAX_AGE\"
            }
          }
        },
        \"warm\": {
          \"min_age\": \"$WARM_MIN_AGE\",
          \"actions\": {
            \"shrink\": {\"number_of_shards\": $WARM_SHRINK_SHARDS},
            \"forcemerge\": {\"max_num_segments\": $WARM_SEGMENTS}
          }
        },
        \"delete\": {
          \"min_age\": \"$DELETE_MIN_AGE\",
          \"actions\": {\"delete\": {}}
        }
      }
    }
  }"
else
  echo "configureIlm=false: skipping ILM policy creation"
fi

echo "Creating component template $TEMPLATE_SETTINGS ..."
if truthy "$CONFIGURE_ILM"; then
  SETTINGS_JSON="\"number_of_shards\": $NUMBER_OF_SHARDS,
      \"number_of_replicas\": $NUMBER_OF_REPLICAS,
      \"index.lifecycle.name\": \"$ILM_POLICY\""
else
  SETTINGS_JSON="\"number_of_shards\": $NUMBER_OF_SHARDS,
      \"number_of_replicas\": $NUMBER_OF_REPLICAS"
fi
curl_put "$ES_URL/_component_template/$TEMPLATE_SETTINGS" "{
  \"template\": {
    \"settings\": {
      $SETTINGS_JSON
    }
  },
  \"_meta\": {
    \"created_by\": \"bash-script\",
    \"created_at\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
  }
}"

echo "Creating component template $TEMPLATE_MAPPINGS ..."
curl_put "$ES_URL/_component_template/$TEMPLATE_MAPPINGS" "{
  \"template\": {
    \"mappings\": {
      \"dynamic_templates\": [
        {
          \"keywords\": {
            \"match_mapping_type\": \"string\",
            \"mapping\": {\"type\": \"keyword\", \"ignore_above\": 256}
          }
        }
      ],
      \"properties\": {
        \"@timestamp\": {\"type\": \"date\"},
        \"message\": {\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},
        \"thread\": {\"type\": \"keyword\"},
        \"level\": {\"type\": \"keyword\"},
        \"logger\": {\"type\": \"keyword\"},
        \"contextName\": {\"type\": \"keyword\"},
        \"mdc\": {\"type\": \"object\", \"dynamic\": true},
        \"context\": {\"type\": \"object\", \"dynamic\": true},
        \"exceptionList\": {
          \"properties\": {
            \"className\": {\"type\": \"keyword\", \"ignore_above\": 256},
            \"message\": {\"type\": \"text\"}
          }
        }
      }
    }
  },
  \"_meta\": {
    \"created_by\": \"bash-script\",
    \"created_at\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
  }
}"

echo "Creating index template $INDEX_TEMPLATE ..."
curl_put "$ES_URL/_index_template/$INDEX_TEMPLATE" "{
  \"index_patterns\": [\"${PREFIX}*\"],
  \"data_stream\": {},
  \"priority\": 300,
  \"composed_of\": [\"$TEMPLATE_SETTINGS\", \"$TEMPLATE_MAPPINGS\"]
}"

echo "Creating data stream $PREFIX ..."
curl -fsS -X PUT "$ES_URL/_data_stream/$PREFIX"

echo "Done. Data stream '$PREFIX' created successfully."

echo
echo "=== VERIFYING DEPLOYED RESOURCES ==="

if truthy "$CONFIGURE_ILM"; then
  echo
  echo "--- ILM Policy ($ILM_POLICY) ---"
  curl -s "$ES_URL/_ilm/policy/$ILM_POLICY?pretty" | jq .
fi

echo
echo "--- Component Template: Settings ($TEMPLATE_SETTINGS) ---"
curl -s "$ES_URL/_component_template/$TEMPLATE_SETTINGS?pretty" | jq .

echo
echo "--- Component Template: Mappings ($TEMPLATE_MAPPINGS) ---"
curl -s "$ES_URL/_component_template/$TEMPLATE_MAPPINGS?pretty" | jq .

echo
echo "--- Index Template ($INDEX_TEMPLATE) ---"
curl -s "$ES_URL/_index_template/$INDEX_TEMPLATE?pretty" | jq .

echo
echo "--- Data Streams Matching '$PREFIX*' ---"
curl -s "$ES_URL/_data_stream/$PREFIX*?pretty" | jq .

echo
echo "Verification complete."
echo
echo "=== SUMMARY ==="
echo "Created data stream: $PREFIX"
echo "Created index template: $INDEX_TEMPLATE"
echo "Created component template (settings): $TEMPLATE_SETTINGS"
echo "Created component template (mappings): $TEMPLATE_MAPPINGS"
if truthy "$CONFIGURE_ILM"; then
  echo "Created ILM policy: $ILM_POLICY"
else
  echo "ILM policy: skipped (--configure-ilm false)"
fi
echo "Settings: shards=$NUMBER_OF_SHARDS replicas=$NUMBER_OF_REPLICAS hot_max_age=$HOT_MAX_AGE hot_rollover_gb=${HOT_ROLLOVER_GB} warm_min_age=$WARM_MIN_AGE delete_min_age=$DELETE_MIN_AGE"
echo
echo "Recommended next step: trigger a rollover once to initialize backing index generation."
echo "curl -X POST \"$ES_URL/$PREFIX/_rollover?pretty\" -H \"Content-Type: application/json\" -d '{}'"
