#!/usr/bin/env bash
# usage: bench.sh <size> [iterations] [tools-csv]
# Runs every scenario for every tool against bench-projects/<size> (generate it first with
# ./gradlew :testing:wrasse-realworld-bench:generateBenchProjects -PbenchSizes=<size>) and appends
# rows to bench-projects/results/<size>.csv; render them with report.py.
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
SIZE=$1
ITERATIONS=${2:-3}
TOOLS=${3:-compile,wrasse,ktlint,ktfmt,detekt}
PROJECT=$HERE/bench-projects/$SIZE
RESULTS=$HERE/bench-projects/results
LOGS=$RESULTS/logs/$SIZE
CSV=$RESULTS/$SIZE.csv
JDK=$(sed -n 's/^org.gradle.java.home=//p' "$PROJECT/gradle.properties")

[ -d "$PROJECT" ] || { echo "no project at $PROJECT"; exit 1; }
mkdir -p "$LOGS"
[ -f "$CSV" ] || echo "size,tool,scenario,iter,seconds,exit,findings,internal_errors,peak_kb,log" > "$CSV"

now() { date +%s.%N; }

stop_daemon() {
    (cd "$PROJECT" && ./gradlew --stop -q >/dev/null 2>&1)
    pkill -f "$JDK.*GradleDaemon" 2>/dev/null
    sleep 1
}

restore_sources() {
    rm -rf "$PROJECT/src"
    cp -r "$PROJECT/src-pristine" "$PROJECT/src"
}

break_file() {
    cp -r "$PROJECT/src-broken/." "$PROJECT/src/main/kotlin/"
}

cold_reset() {
    stop_daemon
    rm -rf "$PROJECT/build" "$PROJECT/.gradle" "$PROJECT/.kotlin" "$PROJECT/.build-cache"
    restore_sources
}

tree_rss_kb() {
    local total=0 pid
    for pid in "$@"; do
        local rss
        rss=$(awk '/^VmRSS:/ {print $2}' "/proc/$pid/status" 2>/dev/null || echo 0)
        total=$((total + ${rss:-0}))
        local children
        children=$(pgrep -P "$pid" 2>/dev/null)
        [ -n "$children" ] && total=$((total + $(tree_rss_kb $children)))
    done
    echo "$total"
}

SAMPLER_PID=""
PEAK_FILE=""
start_sampler() {
    PEAK_FILE=$(mktemp "$LOGS/peak.XXXXXX")
    (
        peak=0
        while true; do
            daemons=$(pgrep -f "$JDK.*GradleDaemon" 2>/dev/null)
            if [ -n "$daemons" ]; then
                cur=$(tree_rss_kb $daemons)
                [ "$cur" -gt "$peak" ] && { peak=$cur; echo "$peak" > "$PEAK_FILE"; }
            fi
            sleep 0.25
        done
    ) &
    SAMPLER_PID=$!
}

stop_sampler() {
    kill "$SAMPLER_PID" 2>/dev/null
    wait "$SAMPLER_PID" 2>/dev/null
    local peak
    peak=$(cat "$PEAK_FILE" 2>/dev/null || echo 0)
    rm -f "$PEAK_FILE"
    echo "${peak:-0}"
}

findings_of() {
    local tool=$1 log=$2
    case $tool in
        wrasse) grep -c "^w: .* wrasse: " "$log" ;;
        ktlint) [ -f "$PROJECT/build/reports/ktlint/ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.txt" ] \
                    && grep -c "" "$PROJECT/build/reports/ktlint/ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.txt" || echo 0 ;;
        ktfmt) grep -c "Invalid formatting for" "$log" ;;
        detekt) [ -f "$PROJECT/build/reports/detekt/main.txt" ] && grep -c "" "$PROJECT/build/reports/detekt/main.txt" || echo 0 ;;
        *) echo 0 ;;
    esac
}

run_step() {
    local tool=$1 scenario=$2 iter=$3
    shift 3
    local log="$LOGS/$tool-$scenario-$iter.log"
    local t0 t1 code
    t0=$(now)
    (cd "$PROJECT" && ./gradlew "$@" --console=plain --no-scan) > "$log" 2>&1
    code=$?
    t1=$(now)
    local seconds findings internal
    seconds=$(echo "$t1 - $t0" | bc)
    findings=$(findings_of "$tool" "$log")
    internal=$(grep -c "wrasse internal error" "$log")
    echo "$SIZE,$tool,$scenario,$iter,$seconds,$code,$findings,$internal,,$log" >> "$CSV"
    printf '  %-8s %-22s iter %s  %7.2fs  exit=%s findings=%s\n' "$tool" "$scenario" "$iter" "$seconds" "$code" "$findings"
}

record_peak() {
    local tool=$1 scenario=$2 iter=$3 peak=$4
    echo "$SIZE,$tool,$scenario,$iter,,,,,$peak," >> "$CSV"
    printf '  %-8s %-22s iter %s  peak=%s MB\n' "$tool" "$scenario" "$iter" "$((peak / 1024))"
}

check_args() {
    case $1 in
        compile) echo "compileKotlin" ;;
        wrasse) echo "compileKotlin -PwrasseCheck" ;;
        ktlint) echo "ktlintMainSourceSetCheck" ;;
        ktfmt) echo "ktfmtCheckMain" ;;
        detekt) echo "detektMain" ;;
    esac
}

compile_args() {
    case $1 in
        wrasse) echo "compileKotlin -PwrasseCheck" ;;
        *) echo "compileKotlin" ;;
    esac
}

run_format() {
    local tool=$1 scenario=$2 iter=$3
    case $tool in
        wrasse)
            run_step "$tool" "$scenario" "$iter" compileKotlin -PwrasseCheck
            run_step "$tool" "$scenario-apply" "$iter" wrasseApply -PwrasseCheck
            ;;
        ktlint) run_step "$tool" "$scenario" "$iter" ktlintMainSourceSetFormat ;;
        ktfmt) run_step "$tool" "$scenario" "$iter" ktfmtFormatMain ;;
    esac
}

has_format() {
    case $1 in wrasse|ktlint|ktfmt) return 0 ;; *) return 1 ;; esac
}

check_session() {
    local tool=$1 iter=$2 peak
    cold_reset
    start_sampler
    run_step "$tool" cold-check "$iter" $(check_args "$tool")
    run_step "$tool" warm-noop-check "$iter" $(check_args "$tool")
    rm -rf "$PROJECT/build"
    run_step "$tool" cached-check "$iter" $(check_args "$tool")
    peak=$(stop_sampler)
    record_peak "$tool" check-session-peak "$iter" "$peak"
}

format_session() {
    local tool=$1 iter=$2 peak
    cold_reset
    start_sampler
    if has_format "$tool"; then
        run_format "$tool" cold-format "$iter"
        run_step "$tool" post-format-compile "$iter" $(compile_args "$tool")
        run_step "$tool" post-format-check "$iter" $(check_args "$tool")
        break_file
        run_format "$tool" inc-format "$iter"
        run_step "$tool" inc-compile "$iter" $(compile_args "$tool")
        run_step "$tool" inc-check "$iter" $(check_args "$tool")
    else
        run_step "$tool" cold-compile "$iter" compileKotlin
        break_file
        run_step "$tool" inc-check "$iter" $(check_args "$tool")
    fi
    if [ "$tool" = wrasse ]; then
        local journal="$PROJECT/build/wrasse/main/wrasse-fixes.txt"
        echo "$SIZE,$tool,journal-bytes,$iter,,,$(stat -c %s "$journal" 2>/dev/null || echo 0),,," >> "$CSV"
    fi
    peak=$(stop_sampler)
    record_peak "$tool" format-session-peak "$iter" "$peak"
}

echo "benchmark $SIZE: tools=$TOOLS iterations=$ITERATIONS project=$PROJECT"
IFS=',' read -ra TOOL_LIST <<< "$TOOLS"
for iter in $(seq 1 "$ITERATIONS"); do
    for tool in "${TOOL_LIST[@]}"; do
        echo "== iteration $iter: $tool"
        check_session "$tool" "$iter"
        format_session "$tool" "$iter"
    done
done
stop_daemon
echo "done: $CSV"
