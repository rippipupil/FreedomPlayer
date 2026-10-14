#!/usr/bin/env python3
"""Comprueba que los Syncthing del APK son programas de Android para su ABI.

Uso: elf-android.py <carpeta jniLibs> [API mínima]

Para cada ABI (arm64-v8a, armeabi-v7a y x86_64) mira <carpeta>/<abi>/libsyncthing.so:
  - que es un ELF little endian de 64 o 32 bits y de la arquitectura de esa ABI (e_machine);
  - que es un ejecutable dinámico con el enlazador de Android (/system/bin/linker64 o
    /system/bin/linker), que solo existe en Android: un binario para Linux lleva otro
    (/lib/ld-linux-…) o ninguno (estático);
  - la nota «Android» que pone el NDK (.note.android.ident), con la versión de Android para la
    que se ha compilado: no puede ser mayor que la API mínima de la app (26). Si no está, solo se
    avisa: lo que dice que es de Android es el enlazador.
Si algo no cuadra, sale con error y lo dice en una anotación de la CI.
"""
import os
import struct
import sys

# ABI: (clase ELF: 1 = 32 bits, 2 = 64 bits), e_machine, nombre, enlazador de Android
ABIS = {
    "arm64-v8a": (2, 183, "ARM de 64 bits (aarch64)", "/system/bin/linker64"),
    "armeabi-v7a": (1, 40, "ARM de 32 bits", "/system/bin/linker"),
    "x86_64": (2, 62, "x86_64", "/system/bin/linker64"),
}
MACHINES = {3: "x86", 8: "MIPS", 40: "ARM de 32 bits", 62: "x86_64", 183: "ARM de 64 bits (aarch64)", 243: "RISC-V"}
PT_INTERP, PT_NOTE, SHT_NOTE = 3, 4, 7
ET_EXEC, ET_DYN = 2, 3


class NoValido(Exception):
    pass


def notes(data, offset, size):
    """Las notas (nombre, tipo, contenido) de un trozo PT_NOTE o SHT_NOTE."""
    end = min(offset + size, len(data))
    pos = offset
    while pos + 12 <= end:
        namesz, descsz, kind = struct.unpack_from("<III", data, pos)
        pos += 12
        name = data[pos:pos + namesz].rstrip(b"\0")
        pos += (namesz + 3) & ~3
        desc = data[pos:pos + descsz]
        pos += (descsz + 3) & ~3
        yield name, kind, desc


def check(path, abi, min_api):
    """Devuelve una línea con lo comprobado, o lanza NoValido con lo que falla."""
    want_class, want_machine, want_name, want_interp = ABIS[abi]
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"\x7fELF":
        raise NoValido("no es un ELF")
    elf_class, elf_data = data[4], data[5]
    if elf_data != 1:
        raise NoValido("no es little endian")
    if elf_class == 2:
        (e_type, e_machine, _, _, e_phoff, e_shoff, e_flags, _, e_phentsize, e_phnum,
         e_shentsize, e_shnum, _) = struct.unpack_from("<HHIQQQIHHHHHH", data, 16)
    elif elf_class == 1:
        (e_type, e_machine, _, _, e_phoff, e_shoff, e_flags, _, e_phentsize, e_phnum,
         e_shentsize, e_shnum, _) = struct.unpack_from("<HHIIIIIHHHHHH", data, 16)
    else:
        raise NoValido(f"clase ELF desconocida ({elf_class})")
    bits = 64 if elf_class == 2 else 32
    if e_machine != want_machine:
        raise NoValido(f"es para {MACHINES.get(e_machine, e_machine)}, y {abi} es {want_name}")
    if elf_class != want_class:
        raise NoValido(f"es de {bits} bits, y {abi} no")
    if e_type not in (ET_EXEC, ET_DYN):
        raise NoValido(f"no es un ejecutable (e_type {e_type})")

    interp = None
    found = []
    for i in range(e_phnum):
        at = e_phoff + i * e_phentsize
        if elf_class == 2:
            p_type, _, p_offset, _, _, p_filesz, _, _ = struct.unpack_from("<IIQQQQQQ", data, at)
        else:
            p_type, p_offset, _, _, p_filesz, _, _, _ = struct.unpack_from("<IIIIIIII", data, at)
        if p_type == PT_INTERP:
            interp = data[p_offset:p_offset + p_filesz].rstrip(b"\0").decode("ascii", "replace")
        elif p_type == PT_NOTE:
            found += notes(data, p_offset, p_filesz)
    if not found and e_shoff:
        for i in range(e_shnum):
            at = e_shoff + i * e_shentsize
            if elf_class == 2:
                _, sh_type, _, _, sh_offset, sh_size, _, _, _, _ = struct.unpack_from("<IIQQQQIIQQ", data, at)
            else:
                _, sh_type, _, _, sh_offset, sh_size, _, _, _, _ = struct.unpack_from("<IIIIIIIIII", data, at)
            if sh_type == SHT_NOTE:
                found += notes(data, sh_offset, sh_size)

    if interp is None:
        raise NoValido("es estático (sin enlazador): no es el compilado para Android")
    if interp != want_interp:
        raise NoValido(f"pide el enlazador {interp}, no el de Android ({want_interp}): es para Linux")

    android = [desc for name, kind, desc in found if name == b"Android" and kind == 1 and len(desc) >= 4]
    extra = ""
    if android:
        api = struct.unpack_from("<i", android[0])[0]
        if api > min_api:
            raise NoValido(f"está compilado para Android {api}, y la app empieza en Android {min_api} (API)")
        ndk = android[0][4:68].rstrip(b"\0").decode("ascii", "replace") if len(android[0]) >= 68 else ""
        extra = f", nota de Android: API {api}" + (f", NDK {ndk}" if ndk else "")
    else:
        print(f"::warning title=Syncthing ({abi})::{path} no lleva la nota «Android» del NDK "
              f"(el enlazador sí es el de Android)")
    if elf_class == 1 and e_machine == 40 and (e_flags & 0xFF000000) != 0x05000000:
        raise NoValido(f"no es EABI 5 (e_flags {e_flags:#x})")
    size = os.path.getsize(path) / 1_000_000
    return f"{abi}: ELF de {bits} bits, {want_name}, enlazador {interp}{extra} ({size:.1f} MB)"


def main():
    if len(sys.argv) not in (2, 3):
        print(__doc__, file=sys.stderr)
        return 2
    root = sys.argv[1]
    min_api = int(sys.argv[2]) if len(sys.argv) == 3 else 26
    ok = True
    for abi in ABIS:
        path = os.path.join(root, abi, "libsyncthing.so")
        if not os.path.isfile(path):
            print(f"::error title=Syncthing ({abi})::Falta {path}")
            ok = False
            continue
        try:
            # Como aviso de la CI: así se ve en el resumen de la ejecución.
            print(f"::notice title=Syncthing ({abi})::{check(path, abi, min_api)}")
        except (NoValido, struct.error) as e:
            print(f"::error title=Syncthing ({abi})::{path}: {e}")
            ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
