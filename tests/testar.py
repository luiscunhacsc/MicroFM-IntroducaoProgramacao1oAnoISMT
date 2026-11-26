"""Verifica??es da edi??o Swing, em pasta tempor?ria e sem depend?ncias Python.
Executar na raiz: python tests/testar.py [--benchmark]
Os testes n?o alteram uma carreira do utilizador.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(command, cwd):
    subprocess.run(command, cwd=cwd, check=True)


def main():
    if not all(shutil.which(tool) for tool in ("java", "javac")):
        raise SystemExit("Instala um JDK 17 ou superior para executar os testes.")
    tests = [ROOT / "tests" / name for name in (
        "TestCareer.java", "TestMatch.java", "TestSwing.java", "BenchmarkRender.java")]
    with tempfile.TemporaryDirectory(prefix="microfm-tests-") as temporary:
        folder = Path(temporary)
        classes = folder / "classes"
        classes.mkdir()
        sources = sorted((ROOT / "src").rglob("*.java"))
        run(["javac", "-encoding", "UTF-8", "--release", "17", "-d", str(classes),
             str(ROOT / "MicroFM.java"), *map(str, sources), *map(str, tests)], folder)
        classpath = os.pathsep.join((str(classes), str(ROOT / "src")))
        java = ["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Djava.awt.headless=true", "-cp", classpath]
        for test in ("TestCareer", "TestMatch", "microfm.ui.TestSwing"):
            run([*java, test], folder)
        if "--benchmark" in sys.argv:
            run([*java, "BenchmarkRender"], folder)
    print("Todos os testes passaram; nenhuma carreira do utilizador foi alterada.")


if __name__ == "__main__":
    main()
