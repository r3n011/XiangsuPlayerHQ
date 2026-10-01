"""把 64 位 ELF 共享库的 PT_LOAD 段重排到 16KB 页对齐（Android 15+ 要求）。

背景：预编译 .so 若用 4KB 对齐链接，在 16KB 页设备上
    - PT_LOAD 的 p_align < 16384，且
    - (p_vaddr - p_offset) % 16384 != 0（congruence 不成立）
会直接 dlopen 失败。本脚本**不改变段内容与虚拟地址**，只做两件事：
    1. 把该段（及其后所有文件内容）整体后移到满足 congruence 的偏移；
    2. 同步更新 program header / section header / e_shoff 中所有受影响的文件偏移，
       并把 PT_LOAD 的 p_align 提升到 16384。
因为虚拟地址不变，段内的地址引用（重定位 addend、dynamic 表等）无需改动。

用法：
    python tools/fix_elf_palign.py <lib.so> [--dry-run]
"""
import struct
import sys
from pathlib import Path

PT_LOAD = 1
PAGE = 16384
EHDR_SHoff = 0x28
PHDR_P_offset = 0x08
PHDR_P_align = 0x30
SHDR_SH_offset = 0x18


def main(argv):
    args = [a for a in argv[1:] if not a.startswith("--")]
    dry_run = "--dry-run" in argv
    if not args:
        print(__doc__)
        return 1

    path = Path(args[0])
    data = bytearray(path.read_bytes())
    if data[:4] != b"\x7fELF" or data[4] != 2:
        print(f"{path} 不是 64 位 ELF，跳过")
        return 1

    e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
    e_shoff = struct.unpack_from("<Q", data, 0x28)[0]
    e_phentsize = struct.unpack_from("<H", data, 0x36)[0]
    e_phnum = struct.unpack_from("<H", data, 0x38)[0]
    e_shentsize = struct.unpack_from("<H", data, 0x3A)[0]
    e_shnum = struct.unpack_from("<H", data, 0x3C)[0]

    phdrs = [
        list(struct.unpack_from("<IIQQQQQQ", data, e_phoff + i * e_phentsize))
        for i in range(e_phnum)
    ]

    # 逆序处理：靠后的段先平移，避免影响靠前段的偏移判断
    for idx in range(e_phnum - 1, -1, -1):
        p_type, _flags, p_off, p_va, _pa, _fsz, _msz, p_align = phdrs[idx]
        if p_type != PT_LOAD:
            continue

        congruent = (p_va - p_off) % PAGE == 0
        if p_align >= PAGE and congruent:
            continue

        # 目标偏移：>= 当前偏移，且满足 (p_vaddr - p_offset) % PAGE == 0
        delta = ((p_va - p_off) % PAGE + PAGE) % PAGE
        target = p_off + delta
        print(
            f"PT_LOAD#{idx}: offset 0x{p_off:x} -> 0x{target:x} (+0x{delta:x}), "
            f"vaddr 0x{p_va:x}, align {p_align} -> {PAGE}"
        )

        if delta and not dry_run:
            # 整体后移 [p_off, EOF)
            data[p_off:p_off] = bytes(delta)

            # 1) program header 的文件偏移
            for j, ph in enumerate(phdrs):
                if ph[2] >= p_off:
                    ph[2] += delta
                    struct.pack_into("<Q", data, e_phoff + j * e_phentsize + PHDR_P_offset, ph[2])

            # 2) section header 表位置
            if e_shoff >= p_off:
                e_shoff += delta
                struct.pack_into("<Q", data, EHDR_SHoff, e_shoff)

            # 3) 各 section 的文件偏移
            for j in range(e_shnum):
                pos = e_shoff + j * e_shentsize + SHDR_SH_offset
                sh_off = struct.unpack_from("<Q", data, pos)[0]
                if sh_off and sh_off >= p_off:
                    struct.pack_into("<Q", data, pos, sh_off + delta)

        # 4) 段对齐提升到 16KB
        if not dry_run:
            struct.pack_into("<Q", data, e_phoff + idx * e_phentsize + PHDR_P_align, PAGE)

    if dry_run:
        print("dry-run：未写回文件")
        return 0

    path.write_bytes(data)
    print(f"已写回 {path}（{len(data)} 字节）")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
