#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
notification_pg_bin=$(pg_config --bindir)
notification_test_port=${NOTIFICATION_TEST_PORT:-55479}
notification_test_dir=$(mktemp -d /tmp/tripbudget-notifications-XXXXXX)
"$notification_pg_bin"/initdb -D "$notification_test_dir/data" -U notification_test --auth=trust > "$notification_test_dir/init.log"
"$notification_pg_bin"/pg_ctl -D "$notification_test_dir/data" -l "$notification_test_dir/postgres.log" -o "-h 127.0.0.1 -p $notification_test_port -k $notification_test_dir" -w start
trap '"$notification_pg_bin"/pg_ctl -D "$notification_test_dir/data" -m fast -w stop' EXIT
"$notification_pg_bin"/createdb -h 127.0.0.1 -p $notification_test_port -U notification_test template_test
export NOTIFICATION_TEST_DB_URL=jdbc:postgresql://127.0.0.1:$notification_test_port/template_test
export NOTIFICATION_TEST_WEBSOCKET=true
mvn -o clean test > "$notification_test_dir/tests.log" 2>&1 || { tail -n 140 "$notification_test_dir/tests.log"; exit 1; }
tail -n 22 "$notification_test_dir/tests.log"
printf 'Disposable test logs: %s\n' "$notification_test_dir"
