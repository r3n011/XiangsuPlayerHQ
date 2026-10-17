#!/usr/bin/env python3
"""PixelPlay 功耗采集脚本（纯 stdlib，无需第三方依赖）。

为什么需要它：在 MIUI / HyperOS 上 `dumpsys batterystats --reset` 会被拒绝
（`Security exception: Package android does not belong to 2000`），所以不能靠
"清零统计"来测一段场景，只能靠**快照差值法**：

  1. 库仑计 `dumpsys battery` 的 `Charge counter`（µAh）—— 硬件真实耗电，最可信；
  2. `dumpsys batterystats --charged` 里目标 UID 的 `(on battery, ...)` 分项
     —— 模型估算，用于把耗电拆成 screen / cpu / audio / video / wakelock。

两者同时取，互为交叉验证。脚本按阶段循环：取快照 -> 执行场景 -> 取快照 -> 打印差值。

典型用法（先建无线 adb 再拔线，避免 USB 充电污染放电数据）：

    adb tcpip 5555 && adb connect 192.168.1.23:5555
    # 拔掉数据线，确认 `dumpsys battery` 里 USB powered: false / status: 3
    python tools/adb_power_bench.py \
        --serial 192.168.1.23:5555 \
        --package com.r3n011.pixelplay \
        --stop-packages com.r3n011.pixelplay.debug,com.theveloper.pixelplayer \
        --prepare \
        --phase idle:180 --phase scroll:360 --phase screenoff:360

详见 docs/power-analysis.md。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time

STATE_DIR = os.path.join(
    os.environ.get("TEMP") or os.environ.get("TMPDIR") or "/tmp",
    "pixelplay-power-bench",
)
STATE_FILE = os.path.join(STATE_DIR, "state.json")

# 我们只关心这几项，其余（cpu:fg / audio:fgs / wifi ...）忽略。
WANTED_KEYS = ("screen", "cpu", "audio", "video", "wakelock")


# --------------------------------------------------------------------------- #
# adb 封装
# --------------------------------------------------------------------------- #
def find_adb(explicit: str | None) -> str:
    for cand in (explicit, os.environ.get("ADB"), shutil.which("adb")):
        if cand and os.path.isfile(cand):
            return cand
    # 退而求其次：从项目 local.properties 的 sdk.dir 推 platform-tools
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    props = os.path.join(root, "local.properties")
    if os.path.isfile(props):
        with open(props, encoding="utf-8", errors="ignore") as fh:
            for line in fh:
                if line.strip().startswith("sdk.dir="):
                    sdk = line.split("=", 1)[1].strip()
                    sdk = sdk.replace("\\:", ":").replace("\\\\", "\\")
                    name = "adb.exe" if os.name == "nt" else "adb"
                    cand = os.path.join(sdk, "platform-tools", name)
                    if os.path.isfile(cand):
                        return cand
    raise SystemExit("找不到 adb：请加 --adb，或设置 ADB 环境变量，或写 local.properties 的 sdk.dir")


class Adb:
    def __init__(self, adb: str, serial: str | None):
        self.base = [adb] + (["-s", serial] if serial else [])

    def shell(self, cmd: str) -> str:
        proc = subprocess.run(
            self.base + ["shell", cmd],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        return (proc.stdout or "") + (proc.stderr or "")

    def shell_quiet(self, cmd: str) -> None:
        subprocess.run(
            self.base + ["shell", cmd],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )

    def install_commands(self) -> bool:
        """光标定确认设备在线。"""
        return "device" in self.shell("echo " + "ok").lower() or bool(self.shell("getprop ro.serialno").strip())


# --------------------------------------------------------------------------- #
# 设备读数
# --------------------------------------------------------------------------- #
def format_uid(uid: int) -> str:
    """复刻 UserHandle.formatUid：1010320 -> 'u10a320'。"""
    user_id, app_id = divmod(uid, 100000)
    if uid < 10000:
        return str(uid)
    return f"u{user_id}a{app_id - 10000}"


def resolve_uid(adb: Adb, package: str) -> tuple[int, str]:
    out = adb.shell(f"pm list packages -U | grep {package}")
    for line in out.splitlines():
        if f"package:{package} " in line or line.strip().startswith(f"package:{package} "):
            match = re.search(r"uid:([\d,]+)", line)
            if match:
                uids = [int(u) for u in match.group(1).split(",") if u]
                # 多用户时取最大的那个（当前用户通常是 appId 较大的一个）
                uid = max(uids)
                return uid, format_uid(uid)
    raise SystemExit(f"设备上找不到包 {package}（pm list packages -U）")


def read_battery(adb: Adb) -> dict:
    out = adb.shell("dumpsys battery")
    data: dict = {}
    for key, pattern in (
        ("level", r"level:\s*(\d+)"),
        ("charge_uah", r"Charge counter:\s*(\d+)"),
        ("status", r"status:\s*(\d+)"),
        ("temperature", r"temperature:\s*(\d+)"),
        ("voltage", r"^\s*voltage:\s*(\d+)"),
    ):
        match = re.search(pattern, out)
        data[key] = int(match.group(1)) if match else None
    data["plugged"] = bool(re.search(r"(AC|USB|Wireless) powered: true", out))
    return data


def read_charged(adb: Adb, uid_str: str) -> dict:
    """解析 `batterystats --charged`，返回 {state: {key: mAh}} 与设备总量。

    state 形如 'screen on' / 'screen off/doze'，只保留以 'on battery' 开头的分组
    （即本次放电窗口内的数据，不会混入充电期间的历史）。
    """
    out = adb.shell("dumpsys batterystats --charged")
    result: dict = {"uid": None, "states": {}, "overall": {}, "device": {}}
    for key, pattern in (
        ("computed_drain", r"Computed drain:\s*([\d.]+)"),
        ("time_on_battery", r"Time on battery:\s*([^\n(]+)"),
        ("discharge", r"\n\s*Discharge:\s*([\d.]+) mAh"),
    ):
        match = re.search(pattern, out)
        result["device"][key] = match.group(1).strip() if match else None

    current_uid = None
    seen_overall = False
    for raw in out.splitlines():
        line = raw.rstrip()
        header = re.match(r"^\s*UID (\S+):", line)
        if header:
            current_uid = header.group(1)
            seen_overall = False
            continue
        if current_uid is None:
            continue
        if re.match(r"^\s*UID \S+:", line):
            continue
        # 另一个 UID 块开始
        if re.match(r"^\s*(UID )?\S+:\s", line) and "=" not in line:
            continue
        state_match = re.match(r"^\s*\(([^)]+)\)\s*(.*)$", line)
        if state_match:
            state, payload = state_match.group(1), state_match.group(2)
            if current_uid == uid_str and state.startswith("on battery"):
                result["states"][state] = _parse_kv(payload)
            continue
        if current_uid == uid_str and not seen_overall and "=" in line:
            result["overall"] = _parse_kv(line)
            seen_overall = True
    result["uid"] = uid_str if result["states"] or result["overall"] else None
    return result


def _parse_kv(payload: str) -> dict:
    values: dict[str, float] = {}
    for key in WANTED_KEYS:
        match = re.search(rf"(?:^|\s){key}=([\d.]+)", payload)
        if match:
            values[key] = float(match.group(1))
    return values


def read_gfxinfo(adb: Adb, package: str) -> dict:
    out = adb.shell(f"dumpsys gfxinfo {package}")
    data: dict = {}
    for key, pattern in (
        ("frames", r"Total frames rendered:\s*(\d+)"),
        ("janky", r"Janky frames:\s*(\d+) \(([\d.]+)%\)"),
        ("p50", r"50th percentile:\s*(\d+)ms"),
        ("p90", r"90th percentile:\s*(\d+)ms"),
        ("p95", r"95th percentile:\s*(\d+)ms"),
        ("p99", r"99th percentile:\s*(\d+)ms"),
    ):
        match = re.search(pattern, out)
        if not match:
            continue
        if key == "janky":
            data["janky"] = int(match.group(1))
            data["janky_pct"] = float(match.group(2))
        else:
            data[key] = int(match.group(1))
    return data


def read_audio(adb: Adb) -> dict:
    """读播放状态与音频通路：offload 通路上会多出 COMPRESS_OFFLOAD/DIRECT 输出线程。

    见 docs/power-analysis.md 第四节：PixelPlay 目前走的是 FLAG_DEEP_BUFFER 的
    MIXER 通路（重采样 + AudioFlinger 混音），本函数就是用来回归这一点的。
    """
    out = adb.shell("dumpsys audio")
    out_flinger = adb.shell("dumpsys media.audio_flinger")
    active = re.search(r"state:started[^\n]*", out)
    sample_rate = None
    if active:
        sr = re.search(r"sampleRate=(\d+)", active.group(0))
        sample_rate = int(sr.group(1)) if sr else None
    # 每个输出线程是一段独立文本，按 "Output thread " 切块后逐块判断。
    blocks = re.split(r"\n(?=Output thread )", out_flinger)
    mixer_active = any("type 0 (MIXER)" in b and "Standby: no" in b for b in blocks)
    return {
        "playing": bool(active),
        "sample_rate": sample_rate,
        "offload_thread": bool(
            re.search(r"AUDIO_OUTPUT_FLAG_(COMPRESS_OFFLOAD|DIRECT)", out_flinger)
        ),
        "mixer_thread": mixer_active,
        "underruns": sum(
            int(m.group(1)) for m in re.finditer(r"underruns=(\d+)", out_flinger)
        ),
    }


def read_thermal(adb: Adb) -> str:
    out = adb.shell("dumpsys thermalservice")
    status = re.search(r"Thermal Status:\s*(\d+)", out)
    battery = re.search(r"Temperature\{mValue=([\d.]+), mType=2", out)
    return f"thermal={status.group(1) if status else '?'} battery={battery.group(1) if battery else '?'}C"


# --------------------------------------------------------------------------- #
# 场景驱动
# --------------------------------------------------------------------------- #
def drive_scroll(adb: Adb, seconds: float, delay_ms: int = 420, span: tuple[int, int] = (2200, 900)) -> int:
    """在列表区上下连续滑动，返回实际滑动次数。用 `wm size` 换算比例。"""
    size = adb.shell("wm size")
    match = re.search(r"(\d+)x(\d+)", size)
    width, height = (int(match.group(1)), int(match.group(2))) if match else (1080, 2400)
    x = width // 2
    top = int(height * 0.72)
    bottom = int(height * 0.28)
    count = 0
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        for _ in range(8):
            if time.monotonic() >= deadline:
                break
            adb.shell_quiet(f"input swipe {x} {top} {x} {bottom} 160")
            time.sleep(delay_ms / 1000)
            count += 1
        for _ in range(8):
            if time.monotonic() >= deadline:
                break
            adb.shell_quiet(f"input swipe {x} {bottom} {x} {top} 160")
            time.sleep(delay_ms / 1000)
            count += 1
    return count


def guard_foreground(adb: Adb, package: str) -> None:
    """阶段开始前的护栏：确认目标 App 真的在前台且屏幕不是黑屏。

    这个护栏来自实战教训：`dumpsys display` 会报 `state ON` 而实际处于 MIUI
    锁屏黑屏态（mDreamingLockscreen=true），此时 gfxinfo 恒为 0 帧、放电数据
    也完全不可信。screencap 的体积可以廉价地区分"真画面"与"黑屏"。
    """
    focus = adb.shell("dumpsys window | grep -e mCurrentFocus").strip()
    if package not in focus:
        print(f"!! 警告：前台不是 {package}\n   mCurrentFocus: {focus}")
    adb.shell_quiet("screencap -p /data/local/tmp/_pp_bench.png")
    size_raw = adb.shell("stat -c %s /data/local/tmp/_pp_bench.png").strip()
    try:
        size_kb = int(size_raw) / 1024.0
    except ValueError:
        return
    if size_kb < 120:
        print(
            f"!! 警告：截图只有 {size_kb:.0f} KB —— 大概率是黑屏/锁屏态，"
            "本轮数据不可用。请解锁手机并保持 App 在前台后重跑。"
        )


def wait_seconds(seconds: float) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        remain = deadline - time.monotonic()
        time.sleep(min(5.0, max(0.1, remain)))


# --------------------------------------------------------------------------- #
# 准备 / 恢复
# --------------------------------------------------------------------------- #
PREPARE_KEYS = {
    "screen_off_timeout": ("system", None),
    "screen_brightness": ("system", None),
    "screen_brightness_mode": ("system", None),
}
STAY_ON = ("global", "stay_on_while_plugged_in")


def prepare(adb: Adb, brightness: int, timeout_ms: int, stop_packages: list[str]) -> None:
    os.makedirs(STATE_DIR, exist_ok=True)
    saved: dict[str, str] = {}
    for key in PREPARE_KEYS:
        namespace = PREPARE_KEYS[key][0]
        saved[f"{namespace}.{key}"] = adb.shell(f"settings get {namespace} {key}").strip()
    saved["global.stay_on_while_plugged_in"] = adb.shell(
        f"settings get {STAY_ON[0]} {STAY_ON[1]}"
    ).strip()
    with open(STATE_FILE, "w", encoding="utf-8") as fh:
        json.dump(saved, fh, indent=2)
    print(f"[prepare] 原设置已存到 {STATE_FILE}: {saved}")

    for package in stop_packages:
        if package:
            adb.shell_quiet(f"am force-stop {package}")
            print(f"[prepare] 已 force-stop {package}")

    adb.shell_quiet(f"settings put system screen_off_timeout {timeout_ms}")
    adb.shell_quiet(f"settings put system screen_brightness_mode 0")
    adb.shell_quiet(f"settings put system screen_brightness {brightness}")
    adb.shell_quiet("settings put global stay_on_while_plugged_in 7")
    adb.shell_quiet("input keyevent KEYCODE_WAKEUP")
    adb.shell_quiet("wm dismiss-keyguard")
    print(f"[prepare] 已固定：亮度 {brightness}/255、屏幕超时 {timeout_ms}ms、常亮")


def restore(adb: Adb) -> None:
    if not os.path.isfile(STATE_FILE):
        print("[restore] 没有 state.json，跳过")
        return
    with open(STATE_FILE, encoding="utf-8") as fh:
        saved = json.load(fh)
    for flat, value in saved.items():
        namespace, key = flat.split(".", 1)
        if value in ("", "null", "None"):
            continue
        adb.shell_quiet(f"settings put {namespace} {key} {value}")
    print(f"[restore] 已按 {STATE_FILE} 恢复原设置")


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def snapshot(adb: Adb, package: str, uid_str: str) -> dict:
    return {
        "at": adb.shell("date").strip(),
        "battery": read_battery(adb),
        "charged": read_charged(adb, uid_str),
        "gfx": read_gfxinfo(adb, package),
        "audio": read_audio(adb),
        "thermal": read_thermal(adb),
    }


def fmt(values: dict) -> str:
    if not values:
        return "-"
    return " ".join(f"{k}={v}" for k, v in values.items())


def report(phases: list[tuple[str, dict, dict, float, int]], uid_str: str) -> None:
    print("\n" + "=" * 78)
    print("功耗汇总（库仑计 = 真实耗电；模型 = batterystats 估算，用于拆解）")
    print("=" * 78)
    header = f"{'阶段':<12}{'时长':>7}{'库仑计Δ':>10}{'平均电流':>10}{'模型Δ':>9}  帧增量/帧率"
    print(header)
    print("-" * 78)
    for name, before, after, seconds, frames_delta in phases:
        d_uah = None
        if before["battery"]["charge_uah"] and after["battery"]["charge_uah"]:
            d_uah = before["battery"]["charge_uah"] - after["battery"]["charge_uah"]
        mah = d_uah / 1000.0 if d_uah is not None else float("nan")
        current = mah / (seconds / 3600.0) if seconds else float("nan")
        model = _device_model_delta(before, after)
        fps = frames_delta / seconds if seconds else 0
        print(
            f"{name:<12}{seconds:>6.0f}s{mah:>9.1f}mAh{current:>9.0f}mA{model:>8.1f}mAh"
            f"  +{frames_delta} ({fps:.0f} fps)"
        )
    print("-" * 78)

    print(f"\n{uid_str} 分项归因（mAh，仅统计放电窗口内的数据）：")
    print(f"{'阶段':<12}{'状态':<20}{'screen':>8}{'cpu':>8}{'audio':>8}{'video':>8}{'wakelock':>10}")
    for name, before, after, _seconds, _frames in phases:
        states = set(before["charged"]["states"]) | set(after["charged"]["states"])
        for state in sorted(states):
            b = before["charged"]["states"].get(state, {})
            a = after["charged"]["states"].get(state, {})
            row = {k: a.get(k, 0.0) - b.get(k, 0.0) for k in WANTED_KEYS}
            if all(abs(v) < 1e-9 for v in row.values()):
                continue
            print(
                f"{name:<12}{state:<20}"
                + "".join(f"{row[k]:>8.2f}" for k in ("screen", "cpu", "audio", "video"))
                + f"{row['wakelock']:>10.2f}"
            )
    print()


def _device_model_delta(before: dict, after: dict) -> float:
    try:
        return float(after["charged"]["device"]["computed_drain"]) - float(
            before["charged"]["device"]["computed_drain"]
        )
    except (TypeError, ValueError):
        return float("nan")


def main() -> int:
    parser = argparse.ArgumentParser(description="PixelPlay 功耗采集（快照差值法）")
    parser.add_argument("--adb", help="adb 可执行文件路径（默认自动探测）")
    parser.add_argument("--serial", help="adb -s 目标（如 192.168.1.23:5555）")
    parser.add_argument("--package", default="com.r3n011.pixelplay", help="被测包名")
    parser.add_argument(
        "--phase",
        action="append",
        default=[],
        metavar="NAME:SECONDS[:MODE]",
        help="阶段，可重复。MODE 为 idle/scroll/off，省略时按阶段名推断。"
        "例：idle:180 / scroll:360 / screenoff:360",
    )
    parser.add_argument("--prepare", action="store_true", help="固定亮度/超时并停掉干扰包")
    parser.add_argument("--restore", action="store_true", help="恢复 --prepare 前的设置")
    parser.add_argument("--brightness", type=int, default=128, help="固定屏幕亮度 0-255")
    parser.add_argument("--timeout-ms", type=int, default=1800000, help="屏幕超时")
    parser.add_argument(
        "--stop-packages",
        default="",
        help="逗号分隔，--prepare 时 force-stop 这些包（同源包会互相污染统计）",
    )
    parser.add_argument("--dry-run", action="store_true", help="只做准备 + 一次快照，不跑阶段")
    args = parser.parse_args()

    adb = Adb(find_adb(args.adb), args.serial)
    serial = adb.shell("getprop ro.serialno").strip()
    if not serial:
        raise SystemExit("设备未连接（adb devices 里看不到目标设备）")
    uid, uid_str = resolve_uid(adb, args.package)
    print(f"[init] 设备 {serial} · 包 {args.package} · uid {uid} ({uid_str})")

    frozen = read_audio(adb)
    print(
        f"[init] 音频：playing={frozen['playing']} sr={frozen['sample_rate']} "
        f"offload={frozen['offload_thread']} mixer={frozen['mixer_thread']}"
    )

    if args.prepare:
        prepare(adb, args.brightness, args.timeout_ms,
                [p for p in args.stop_packages.split(",") if p])

    battery = read_battery(adb)
    if battery["plugged"]:
        print("!! 注意：设备正在充电（AC/USB powered: true），放电数据会被污染。"
              "建议先 `adb tcpip 5555` + 无线连接后拔掉数据线，直到 status: 3。")

    if args.dry_run or not args.phase:
        snap = snapshot(adb, args.package, uid_str)
        print(json.dumps(snap, ensure_ascii=False, indent=2))
        return 0

    phases: list[tuple[str, dict, dict, float, int]] = []
    for spec in args.phase:
        parts = spec.split(":")
        name, seconds = parts[0], float(parts[1])
        # 显式 mode 优先；否则从阶段名推断：含 scroll -> 滚动，含 off -> 息屏。
        if len(parts) > 2:
            mode = parts[2]
        elif "scroll" in name:
            mode = "scroll"
        elif "off" in name:
            mode = "off"
        else:
            mode = "idle"

        before = snapshot(adb, args.package, uid_str)
        print(f"[{name}] 起点 {before['at']} · {before['thermal']} · 播放={before['audio']['playing']}")
        if seconds < 120:
            print(
                f"!! 警告：阶段 {name} 只有 {seconds:.0f}s。库仑计刷新率约 30-60s，"
                "短于 3 分钟的阶段放电差值不可靠，建议 >=180s。"
            )
        guard_foreground(adb, args.package)

        if mode == "off":
            adb.shell_quiet("input keyevent KEYCODE_SLEEP")
        elif mode == "scroll":
            adb.shell_quiet("input keyevent KEYCODE_WAKEUP")
            adb.shell_quiet("wm dismiss-keyguard")

        started = time.monotonic()
        if mode == "scroll":
            swipes = drive_scroll(adb, seconds)
            print(f"[{name}] 滑动 {swipes} 次")
        else:
            wait_seconds(seconds)
        elapsed = time.monotonic() - started

        after = snapshot(adb, args.package, uid_str)
        if mode == "off":
            adb.shell_quiet("input keyevent KEYCODE_WAKEUP")
            adb.shell_quiet("wm dismiss-keyguard")
        frames = max(0, after["gfx"].get("frames", 0) - before["gfx"].get("frames", 0))
        print(f"[{name}] 终点 {after['at']} · {after['thermal']} · 播放={after['audio']['playing']}")
        phases.append((name, before, after, elapsed, frames))

    report(phases, uid_str)

    if args.restore:
        restore(adb)
    return 0


if __name__ == "__main__":
    sys.exit(main())
