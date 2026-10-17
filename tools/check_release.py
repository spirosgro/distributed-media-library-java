"""Targeted public-source packaging checks; Python standard library only."""
from pathlib import Path
import re
import ast

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = ["README.md", "LICENSE", "CITATION.cff", "SECURITY.md", ".gitignore", ".gitattributes",
            ".github/workflows/test.yml", "tools/Build.java", "assets/client-preview.png",
            "docs/architecture.md", "docs/demo.md", "docs/verification.md", "docs/provenance.md",
            "docs/github-upload.md", "data/README.md", "references/README.md"]
SKIP = {"build", ".git", ".idea", ".vscode", "__pycache__"}
TEXT_SUFFIXES = {".java", ".py", ".md", ".yml", ".cff", ".gitattributes", ".gitignore"}


def public_files():
    for path in sorted(ROOT.rglob("*")):
        if path.is_file() and not any(part in SKIP for part in path.relative_to(ROOT).parts):
            yield path


def main():
    errors = []
    for name in REQUIRED:
        if not (ROOT / name).is_file():
            errors.append(f"Missing required public file: {name}")
    files = list(public_files())
    for path in files:
        relative = path.relative_to(ROOT)
        if path.suffix.lower() in {".ser", ".class", ".jar", ".zip", ".pdf", ".docx", ".iml", ".log"}:
            errors.append(f"Unexpected generated/private file: {relative}")
        if relative.parts[0] == "data" and relative.as_posix() != "data/README.md":
            errors.append(f"Runtime data in release: {relative}")
        if path.suffix in TEXT_SUFFIXES or path.name in {"LICENSE", ".gitignore", ".gitattributes"}:
            content = path.read_text(encoding="utf-8")
            if re.search(r"[\u0370-\u03ff\u1f00-\u1fff]", content):
                errors.append(f"Greek text in English public files: {relative}")
            if re.search(r"[A-Za-z]:[\\/]Users[\\/]", content):
                errors.append(f"Personal filesystem path: {relative}")
            if re.search("icsd" + r"\d{5,}", content, re.I):
                errors.append(f"Student identifier: {relative}")
            if path.suffix == ".py":
                ast.parse(content, filename=str(relative))
            if path.suffix == ".md":
                for target in re.findall(r"!?\[[^\]]*\]\(([^)]+)\)", content):
                    if target.startswith(("https://", "http://", "#", "mailto:")):
                        continue
                    destination = (path.parent / target.split("#", 1)[0]).resolve()
                    if not destination.is_relative_to(ROOT) or not destination.exists():
                        errors.append(f"Broken/local-external link in {relative}: {target}")
    java = list((ROOT / "src/main/java").rglob("*.java"))
    for name in ("Request", "RequestType", "Response", "Media", "Movie", "Series", "Episode"):
        if sum(path.stem == name for path in java) != 1:
            errors.append(f"Expected exactly one shared source definition for {name}")
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"PASS: {len(files)} public files; required content, local links, shared types and publication checks.")
    print("This is a targeted release check, not a comprehensive secret or security audit.")


if __name__ == "__main__":
    main()
