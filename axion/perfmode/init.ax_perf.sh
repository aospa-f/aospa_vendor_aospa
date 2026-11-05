#!/vendor/bin/sh
# Axion BGMI performance mode - WALT profile applier.
#
# Started by init when persist.sys.ax_perf_mode changes. The mode arrives
# as the first argument: "on" (performance) or "off" (balanced).
# CPU/GPU frequency limits are handled by AxionParts through the Axion
# kernel manager; this script only covers the root-only WALT tunables for
# both modes.
#
# Touch sampling is intentionally not touched: this device's touch firmware
# (HBP pipeline) owns the sampling rate and exposes no kernel nodes for it.

MODE="$1"

WALT_SYS="/proc/sys/walt"
CPU_WALT="/sys/devices/system/cpu/cpufreq"

if [ "$MODE" = "on" ]; then
    # WALT scheduling: lower migration thresholds, earlier task packing.
    echo "65 90" > "$WALT_SYS/sched_upmigrate" 2>/dev/null
    echo "55 80" > "$WALT_SYS/sched_downmigrate" 2>/dev/null
    echo 95 > "$WALT_SYS/sched_group_upmigrate" 2>/dev/null
    echo 75 > "$WALT_SYS/sched_group_downmigrate" 2>/dev/null

    # WALT per-policy hispeed: jump to high clusters sooner.
    echo 1500000 > "$CPU_WALT/policy0/walt/hispeed_freq" 2>/dev/null
    echo 1800000 > "$CPU_WALT/policy3/walt/hispeed_freq" 2>/dev/null
    echo 2000000 > "$CPU_WALT/policy7/walt/hispeed_freq" 2>/dev/null
    echo 80 > "$CPU_WALT/policy3/walt/hispeed_load" 2>/dev/null
    echo 80 > "$CPU_WALT/policy7/walt/hispeed_load" 2>/dev/null

    # Input boost: silver + gold clusters, prime untouched.
    echo "1500000 1500000 1500000 1500000 1500000 1500000 1500000 0" > "$WALT_SYS/input_boost/input_boost_freq" 2>/dev/null
else
    # WALT scheduling: stock values from init.kernel.post_boot.sh.
    echo "71 95" > "$WALT_SYS/sched_upmigrate" 2>/dev/null
    echo "65 85" > "$WALT_SYS/sched_downmigrate" 2>/dev/null
    echo 100 > "$WALT_SYS/sched_group_upmigrate" 2>/dev/null
    echo 85 > "$WALT_SYS/sched_group_downmigrate" 2>/dev/null

    # WALT per-policy hispeed: stock values.
    echo 1113600 > "$CPU_WALT/policy0/walt/hispeed_freq" 2>/dev/null
    echo 1190400 > "$CPU_WALT/policy3/walt/hispeed_freq" 2>/dev/null
    echo 1459200 > "$CPU_WALT/policy7/walt/hispeed_freq" 2>/dev/null
    echo 85 > "$CPU_WALT/policy3/walt/hispeed_load" 2>/dev/null
    echo 85 > "$CPU_WALT/policy7/walt/hispeed_load" 2>/dev/null

    # Input boost: stock silver-only boost.
    echo "1113600 0 0 0 0 0 0 0" > "$WALT_SYS/input_boost/input_boost_freq" 2>/dev/null
fi

exit 0
