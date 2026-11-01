#!/usr/bin/env python3
"""Run a destructive regression fixture only in a newly created, loopback-only test server."""
import argparse
import hashlib
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import urllib.request

FILES = {
    "26.2": ("paper-26.2-134.jar", "https://fill-data.papermc.io/v1/objects/3cce0a4922baf79bd8e1275ee5ff9f073f24675770e09b0d4be9442d513e7a6b/paper-26.2-134.jar", "sha256", "3cce0a4922baf79bd8e1275ee5ff9f073f24675770e09b0d4be9442d513e7a6b"),
    "26.3": ("paper-26.3-206.jar", "https://fill-data.papermc.io/v1/objects/4208a692538104ec88699c0e078b2b89842920a10472ebb37e72cdda27c0d80e/paper-26.3-206.jar", "sha256", "4208a692538104ec88699c0e078b2b89842920a10472ebb37e72cdda27c0d80e"),
    "worldedit": ("worldedit-bukkit-7.4.6.jar", "https://cdn.modrinth.com/data/1u6JkXh5/versions/YFnqiUlL/worldedit-bukkit-7.4.6.jar", "sha512", "1fe3975132857093f971fed9efc601299bfa76fa7885f52a542fee41e7a0165ca9cb3cbcf5ae244474887d774d650b0db6f8f79aa80f98b245dea44f1c1b4152"),
    "fawe": ("FastAsyncWorldEdit-Paper-2.16.1.jar", "https://github.com/IntellectualSites/FastAsyncWorldEdit/releases/download/2.16.1/FastAsyncWorldEdit-Paper-2.16.1.jar", "sha256", "a1706c41412a108e3d2fdbd9e9dd0c60904f2ba45414c6edf11c1262812e1838"),
}


def download(key, folder):
    name, url, algorithm, expected = FILES[key]
    path = folder / name
    if not path.exists():
        request = urllib.request.Request(url, headers={"User-Agent": "CataMinesForkValidation/1.0 (https://github.com/mitsuba7891/catamines-fork)"})
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read()
        if hashlib.new(algorithm, data).hexdigest() != expected:
            raise RuntimeError(f"Checksum mismatch: {name}")
        path.write_bytes(data)
    if hashlib.new(algorithm, path.read_bytes()).hexdigest() != expected:
        raise RuntimeError(f"Cached checksum mismatch: {name}")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-eula", action="store_true", help="Explicitly accept https://aka.ms/MinecraftEULA for this disposable server")
    parser.add_argument("--paper-version", choices=["26.2", "26.3"], required=True)
    parser.add_argument("--provider", choices=["worldedit", "fawe"], required=True)
    parser.add_argument("--directory", type=Path, default=Path("/tmp/opencode/catamines-validation"))
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error("Read the Minecraft EULA and supply --accept-eula only if you accept it.")
    repo = Path(__file__).resolve().parent.parent
    plugin = repo / "build/libs/catamines-fork-3.1.0.jar"
    smoke = repo / "build/libs/catamines-smoke-tests-3.1.0.jar"
    if not plugin.is_file() or not smoke.is_file():
        parser.error("Build with ./gradlew shadowJar smokeJar first.")
    downloads = args.directory.resolve() / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    paper, provider = download(args.paper_version, downloads), download(args.provider, downloads)
    run = Path(tempfile.mkdtemp(prefix=f"{args.paper_version}-{args.provider}-", dir=args.directory.resolve()))
    plugins = run / "plugins"
    plugins.mkdir()
    shutil.copy2(paper, run / "paper.jar")
    for artifact in [provider, plugin, smoke]:
        shutil.copy2(artifact, plugins / artifact.name)
    (plugins / "bStats").mkdir()
    (plugins / "bStats/config.yml").write_text("enabled: false\n", encoding="utf-8")
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    (run / "eula.txt").write_text("# Explicitly authorized by the caller for this test only.\neula=true\n", encoding="utf-8")
    (run / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\n"
        "view-distance=2\nsimulation-distance=2\nmax-players=1\n"
        "enable-query=false\nenable-rcon=false\npause-when-empty-seconds=-1\n",
        encoding="utf-8",
    )
    print(f"Isolated server: {run}", flush=True)
    log = run / "console.log"
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(["java", "-Xms512M", "-Xmx2G", "-Dcom.mojang.eula.agree=true", "-jar", "paper.jar", "--nogui"],
                                   cwd=run, stdout=output, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        try:
            code = process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise RuntimeError(f"Server timed out; see {log}")
    result = run / "smoke-result.txt"
    if code != 0 or not result.exists() or not result.read_text().startswith("PASS "):
        print("\n".join(log.read_text(errors="replace").splitlines()[-100:]))
        raise RuntimeError(f"Smoke test failed; see {log}")
    print(result.read_text().strip())


if __name__ == "__main__":
    main()
