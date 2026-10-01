"""检查 ELF 共享库的段对齐（Android 15+ 要求 16KB 页对齐：所有 PT_LOAD 的 p_align >= 16384）。

用法：
    python tools/check_elf_palign.py <文件或目录> [...]

输出每个 .so 的最大 LOAD 段对齐值，并标记是否满足 16KB 对齐。
"""
import struct
import sys
from pathlib import Path

PT_LOAD = 1


def read_palign(path: Path):
    """返回该 ELF 所有 PT_LOAD 段的 p_align 列表；非 ELF 返回 None。"""
    with path.open("rb") as f:
        data = f.read()
    if len(data) < 64 or data[:4] != b"\x7fELF":
        return None
    is64 = data[4] == 2
    little = data[5] == 1
    endian = "<" if little else ">"
    if is64:
        e_phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
        e_phentsize = struct.unpack_from(endian + "H", data, 0x36)[0]
        e_phnum = struct.unpack_from(endian + "H", data, 0x38)[0]
        aligns = []
        for i in range(e_phnum):
            off = e_phoff + i * e_phentsize
            p_type = struct.unpack_from(endian + "I", data, off)[0]
            p_align = struct.unpack_from(endian + "Q", data, off + 0x30)[0]
            if p_type == PT_LOAD:
                aligns.append(p_align)
        return aligns
    e_phoff = struct.unpack_from(endian + "I", data, 0x1C)[0]
    e_phentsize = struct.unpack_from(endian + "H", data, 0x2A)[0]
    e_phnum = struct.unpack_from(endian + "H", data, 0x2C)[0]
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        p_align = struct.unpack_from(endian + "I", data, off + 0x1C)[0]
        if p_type == PT_LOAD:
            aligns.append(p_align)
    return aligns


def main(argv):
    show_all = "--all" in argv
    args = [a for a in argv[1:] if not a.startswith("--")]
    targets = []
    for raw in args:
        p = Path(raw)
        if p.is_dir():
            targets.extend(sorted(p.rglob("*.so")))
        elif p.exists():
            targets.append(p)
    if not targets:
        print("没有找到可检查的 .so")
        return 1

    bad = []
    for so in targets:
        try:
            aligns = read_palign(so)
        except Exception as exc:  # noqa: BLE001
            print(f"[?] {so} 读取失败: {exc}")
            continue
        if aligns is None:
            print(f"[?] {so} 不是 ELF")
            continue
        worst = min(aligns) if aligns else 0
        ok = worst >= 16384
        if not ok:
            bad.append(so)
        if show_all or not ok:
            print(f"[{'OK ' if ok else 'BAD'}] align={worst:<7} {so}")

    print()
    if bad:
        print(f"不满足 16KB 对齐的库（{len(bad)} 个）：")
        for so in bad:
            print(f"  - {so}")
        return 2
    print("全部满足 16KB 段对齐 ✅")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
