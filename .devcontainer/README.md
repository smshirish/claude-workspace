# Dev Container Setup Guide

Reproducible VS Code Dev Container for the `claude-workspace` repo (Claude Code + Java/Maven + Node/Playwright for `finance-app`).

**Contents**
1. [Setup sequence (git clone to running container)](#1-setup-sequence-from-git-clone-to-a-running-container)
2. [Required software on the host](#2-required-software-on-the-host)
3. [Files in this folder](#3-files-in-this-folder)
4. [How the environment is built (layers)](#4-how-the-environment-is-built-layers)
5. [Installed software inventory](#5-installed-software-inventory)
6. [Mounts and shared folders](#6-mounts-and-shared-folders)
7. [File permissions and the `node` user](#7-file-permissions-and-the-node-user)
8. [Building and publishing the custom image](#8-building-and-publishing-the-custom-image)
9. [Audit findings and known gaps](#9-audit-findings-and-known-gaps)
10. [Troubleshooting](#10-troubleshooting)
11. [Reference links](#11-reference-links)

---

## 1. Setup sequence (from `git clone` to a running container)

The repo contains everything needed, including the `Dockerfile`, so the whole environment can be rebuilt from a plain git checkout. No pre-built image or registry access is required.

### Sequence at a glance

| Step | Action | Runs on | Software needed at this step |
|------|--------|---------|------------------------------|
| 0 | Install prerequisites | Host | Virtualization + WSL 2 (Windows), container engine, VS Code, Dev Containers extension, git |
| 1 | Clone the repo | Host | **git** |
| 2 | Build the image locally from `.devcontainer/Dockerfile` | Host | **Docker or Podman** (+ internet to pull the base image from `mcr.microsoft.com`) |
| 3 | Open the folder in VS Code and "Reopen in Container" | Host (VS Code) | **VS Code + Dev Containers extension + running container engine**; internet for features |
| 4 | Container is created (automatic) | Container engine | Internet access to `ghcr.io` (features), VS Code Marketplace (extension) |
| 5 | `postCreateCommand` runs (automatic) | Inside container | Internet access to npm registry and Playwright CDN. Tools used (`sudo`, `apt`, `node`, `npm`) already exist in the image |
| 6 | Verify | Inside container | none |
| 7 | Log in to Claude Code | Inside container | Anthropic account |

### Step 0 - Install prerequisites (once per machine)
Install everything marked **Required** in [section 2](#2-required-software-on-the-host). Then confirm on the host:

```bash
git --version
docker --version          # or: podman --version
code --version
docker info               # must succeed, i.e. the engine is running
```

### Step 1 - Clone the repository (needs: git)
```bash
git clone <repo-url> claude-workspace
cd claude-workspace
```
On Windows, clone onto a Windows drive path (here `C:\Users\shiri\workspace\claude-workspace`) and run the commands below from PowerShell or Git Bash in that folder. The folder will be bind-mounted into the container as `/workspaces/claude-workspace`.

The checkout contains:
```
.devcontainer/Dockerfile            <- recipe for the custom image
.devcontainer/devcontainer.json     <- how VS Code builds/starts the container
.devcontainer/devcontainer-lock.json
```

### Step 2 - Build the image locally (needs: Docker **or** Podman)
`devcontainer.json` expects an image named `localhost/my-custom-devcontainer`. It exists only in your local engine's image store, so build it from the checked-out Dockerfile (build context = the `.devcontainer` folder):

```bash
# Docker
docker build -t localhost/my-custom-devcontainer .devcontainer/

# Podman (e.g. inside WSL, or in a Podman Desktop terminal)
podman build -t localhost/my-custom-devcontainer .devcontainer/
```

What this does: pulls `mcr.microsoft.com/devcontainers/javascript-node:20`, then `apt-get install`s OpenJDK 21 and Maven on top (roughly 1-5 minutes on first run, needs internet). Nothing on the host except the engine is used.

Check it exists:
```bash
docker images | grep my-custom-devcontainer     # or: podman images
```

Notes:
- **The image must be built in the same engine VS Code will use** in step 3. An image built with Podman is not visible to Docker Desktop, and vice versa.
- If VS Code should use Podman, set VS Code setting `dev.containers.dockerPath` to `podman` (and make sure Podman's Docker-compatible socket/service is running).
- **Skip this step entirely** by changing `devcontainer.json` to build automatically (see [section 9, item 1](#9-audit-findings-and-known-gaps)):
  ```jsonc
  "build": { "dockerfile": "Dockerfile" }   // replaces "image": "localhost/my-custom-devcontainer"
  ```
- **Alternative to building:** pull the published copy instead (needs `docker login ghcr.io` with a `read:packages` PAT if the package is private):
  ```bash
  docker pull ghcr.io/smshirish/my-custom-devcontainer:latest
  docker tag  ghcr.io/smshirish/my-custom-devcontainer:latest localhost/my-custom-devcontainer
  ```

### Step 3 - Open in the container (needs: VS Code + Dev Containers extension + running engine)
```bash
code .
```
Then press **F1 -> `Dev Containers: Reopen in Container`** (or accept the pop-up "Folder contains a Dev Container configuration file").

### Step 4 - Container creation (automatic)
VS Code, via the container engine:
1. Starts a container from `localhost/my-custom-devcontainer` with `--cap-add=NET_ADMIN --cap-add=NET_RAW`.
2. Bind-mounts the project at `/workspaces/claude-workspace`.
3. Downloads and applies the features (`node`, `claude-code`) from `ghcr.io`, pinned by `devcontainer-lock.json`.
4. Installs the `anthropic.claude-code` VS Code extension and the VS Code server.
5. Switches to user `node` and forwards port 8080.

### Step 5 - `postCreateCommand` (automatic, first creation only)
Runs inside the container as `node`:
1. `sudo apt-get install openjdk-21-jdk-headless maven` (already present from the Dockerfile, so this is a no-op)
2. Appends `JAVA_HOME`/`PATH` to `~/.bashrc`
3. `cd finance-app/e2e && npm ci` (installs exactly what `package-lock.json` specifies)
4. `npx playwright install chromium` and `npx playwright install-deps chromium` (downloads a browser, installs OS libs via sudo)

Takes a few minutes. Watch progress via **View -> Output -> Dev Containers** or the "show log" link in the VS Code notification.

### Step 6 - Verify (inside the container terminal)
```bash
whoami                 # node
java -version          # OpenJDK 21
mvn -version           # Maven 3.9.x
node -v && npm -v      # Node 24.x
claude --version       # Claude Code CLI
npx playwright --version   # run from finance-app/e2e
```

### Step 7 - Log in to Claude Code
```bash
claude
```
Credentials are stored in `~/.claude`, which is **not persisted** across container rebuilds unless you add the volume mount in [section 6](#6-mounts-and-shared-folders).

### What requires what - summary

| Software | Needed to clone | Needed to build image | Needed to run container | Needed inside container |
|----------|:---:|:---:|:---:|:---:|
| git (host) | yes | | | |
| Docker / Podman (host) | | yes | yes | |
| VS Code + Dev Containers ext. (host) | | | yes | |
| Internet: `mcr.microsoft.com`, Debian apt mirrors | | yes | | |
| Internet: `ghcr.io`, VS Code Marketplace | | | yes | |
| Internet: npm registry, Playwright CDN | | | | yes (postCreate) |
| JDK, Maven, Node, Claude CLI, Playwright | | | | provided by the image/features |

### Rebuild cycle after editing the Dockerfile
1. Re-run the step 2 build command.
2. **F1 -> `Dev Containers: Rebuild Container`** (use "Rebuild Without Cache" if the change isn't picked up).

---

## 2. Required software on the host

The "host" is the machine running VS Code and the container engine, not the container itself.

### Windows 10 (21H2+) / Windows 11 (the setup used here)

| # | Software | Required? | Purpose | Link |
|---|----------|-----------|---------|------|
| 1 | Virtualization enabled in BIOS/UEFI | Required | WSL2 and containers need hardware virtualization | [Microsoft docs](https://learn.microsoft.com/windows/wsl/troubleshooting#error-0x80070003-when-installing-a-distribution) |
| 2 | WSL 2 (+ a Linux distro, e.g. Ubuntu) | Required | Linux kernel backing the container engine | [Install WSL](https://learn.microsoft.com/windows/wsl/install) |
| 3 | Docker-compatible container engine, e.g. Docker Desktop (WSL 2 backend) | Required | Runs the dev container | [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/) |
| 4 | Visual Studio Code | Required | IDE | [Download](https://code.visualstudio.com/download) |
| 5 | VS Code extension **Dev Containers** (`ms-vscode-remote.remote-containers`) | Required | "Reopen in Container" support | [Marketplace](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) |
| 6 | Git for Windows | Required | Clone the repo; VS Code shares git credentials with the container | [Download](https://git-scm.com/download/win) |
| 7 | Podman (inside WSL / Podman Desktop) | Optional | Only to **build and push** the custom image to GHCR (the author's workflow). `docker build` works equally well | [Podman install](https://podman.io/docs/installation) |
| 8 | GitHub account + Personal Access Token | Optional | `write:packages` to push the image, `read:packages` to pull it if the package is private | [PAT docs](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) |
| 9 | Anthropic account / API access | Required for Claude Code | Log in with `claude` inside the container | [Claude Code docs](https://docs.anthropic.com/en/docs/claude-code/overview) |

The workspace is bind-mounted from the Windows drive, so clone the repo to a Windows path (here: `C:\Users\shiri\workspace\claude-workspace`).

### Linux host

| Software | Required? | Notes | Link |
|----------|-----------|-------|------|
| Docker Engine (or Podman with Docker socket compatibility) | Required | Add your user to the `docker` group so no `sudo` is needed | [Install Docker Engine](https://docs.docker.com/engine/install/) |
| Visual Studio Code | Required | | [Download](https://code.visualstudio.com/download) |
| Dev Containers extension | Required | | [Marketplace](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) |
| git | Required | | `sudo apt install git` |
| Podman | Optional | Only if you prefer it for building the image | [Podman install](https://podman.io/docs/installation) |

macOS is not tested for this setup. It should work with Docker Desktop for Mac, but bind-mount permission behaviour differs.

### Not needed on the host

Java, Maven, Node.js, npm, Playwright and the Claude Code CLI are all provided **inside** the container. Do not install them on the host for this project.

---

## 3. Files in this folder

| File | Purpose |
|------|---------|
| `devcontainer.json` | Dev container definition: image, features, extensions, user, ports, env, post-create command. [Reference](https://containers.dev/implementors/json_reference/) |
| `Dockerfile` | Builds the custom base image (adds JDK 21 + Maven). |
| `devcontainer-lock.json` | Pins the exact versions/digests of the devcontainer features for reproducible builds. Commit it. |
| `dockerfilereadme.md` | Author's personal notes on publishing the image to GHCR. Contains a token placeholder; **never commit a real PAT**. |
| `README.md` | This file. |

---

## 4. How the environment is built (layers)

```
 1. Base image   mcr.microsoft.com/devcontainers/javascript-node:20   (Debian 13 "trixie", image v4.0.11)
 2. Dockerfile   + OpenJDK 21 headless, + Maven, JAVA_HOME             -> localhost/my-custom-devcontainer
 3. Features     + Node 24 (nvm, pinned `"version": "24"`) via devcontainers/features/node:1
                 + Claude Code CLI via anthropics/devcontainer-features/claude-code:1
 4. VS Code      + extension anthropic.claude-code
 5. postCreate   + finance-app/e2e: npm install, Playwright Chromium
```

### 4.1 Base image
[`javascript-node`](https://github.com/devcontainers/images/tree/main/src/javascript-node) from Microsoft. Provides the non-root `node` user (uid/gid 1000), passwordless `sudo`, git, curl, wget, zsh + oh-my-zsh, Python 3, and the `nvm`/`npm` groups. It also includes Node.js 20.20.2 at `/usr/local/bin/node`, which is shadowed on PATH by the Node 24 added by the `node` feature (see [section 9, item 3](#9-audit-findings-and-known-gaps)).

### 4.2 Custom Dockerfile
```dockerfile
FROM mcr.microsoft.com/devcontainers/javascript-node:20
USER root
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-21-jdk-headless maven \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"
USER node
```
Purpose: bake slow installs (JDK, Maven) into the image so container creation is fast.

### 4.3 Dev container features
[Features](https://containers.dev/features) are installable units applied on top of the image at container creation. Versions are pinned in `devcontainer-lock.json`:

| Feature | Locked version | Source |
|---------|----------------|--------|
| `ghcr.io/devcontainers/features/node:1` (option `"version": "24"`) | 1.7.1 | [repo](https://github.com/devcontainers/features/tree/main/src/node) |
| `ghcr.io/anthropics/devcontainer-features/claude-code:1` | 1.0.5 | [repo](https://github.com/anthropics/devcontainer-features) |

### 4.4 devcontainer.json settings

| Setting | Value | Meaning |
|---------|-------|---------|
| `image` | `localhost/my-custom-devcontainer` | Local custom image (see section 1) |
| `remoteUser` | `node` | VS Code and terminals run as `node`, not root |
| `forwardPorts` | `[8080]` | finance-app dev server |
| `runArgs` | `--cap-add=NET_ADMIN`, `--cap-add=NET_RAW` | Network capabilities (needed only by the optional firewall script, see section 9) |
| `remoteEnv` | `JAVA_HOME`, `PATH` | Java/Maven on PATH for VS Code processes |
| `customizations.vscode.extensions` | `anthropic.claude-code` | Auto-installed in the container |
| `postCreateCommand` | apt install, npm install, Playwright | Runs once after creation |

Docs: [Dev Containers overview](https://code.visualstudio.com/docs/devcontainers/containers), [devcontainer.json reference](https://containers.dev/implementors/json_reference/), [Claude Code dev container guide](https://docs.anthropic.com/en/docs/claude-code/devcontainer).

---

## 5. Installed software inventory

Verified by inspecting the running container.

| Software | Version | Installed by |
|----------|---------|--------------|
| Debian | 13 (trixie) | Base image |
| bash / zsh + oh-my-zsh | 5.2.37 / 5.9 | Base image |
| git | 2.47.3 | Base image |
| curl / wget | 8.14.1 / 1.25.0 | Base image |
| Python 3 + pip | 3.13.5 / 25.1.1 | Base image |
| OpenJDK 21 (headless) | 21.0.11 | **Dockerfile** |
| Apache Maven | 3.9.9 | **Dockerfile** |
| Node.js (active) | 24.16.0 (major pinned to 24; under `/usr/local/share/nvm`) | node feature |
| Node.js (inactive, shadowed) | 20.20.2 (`/usr/local/bin/node`) | Base image |
| npm / pnpm / corepack | 11.13.0 / 11.5.0 / 0.35.0 | node feature |
| Claude Code CLI | 2.1.159 | claude-code feature (global npm) |
| Playwright + Chromium | 1.60.0 (exact pin in `finance-app/e2e/package.json` + `package-lock.json`) | **postCreateCommand** (`finance-app/e2e`) |
| VS Code extension `anthropic.claude-code` | latest | devcontainer.json |

Playwright: [docs](https://playwright.dev/docs/intro).

**Version locking:** `@playwright/test` is pinned to the exact version `1.60.0` (no `^`) in `finance-app/e2e/package.json`, and `postCreateCommand` uses `npm ci`, which fails rather than drift from `package-lock.json`. `npx playwright install chromium` downloads the Chromium build matching that version. Not pinned: the OS libraries added by `npx playwright install-deps chromium` (they come from Debian `apt` at whatever version is current). To upgrade Playwright, change the version in `package.json`, run `npm install` in `finance-app/e2e`, and commit both files.

---

## 6. Mounts and shared folders

Verified with `findmnt` inside the container:

| Container path | Source | Notes |
|----------------|--------|-------|
| `/workspaces/claude-workspace` | `C:\Users\shiri\workspace\claude-workspace` (Windows host, 9p) | Added automatically by VS Code. **Already a host-shared folder**: edit from Windows or as `node`; changes are seen on both sides. |
| `/vscode` | Docker volume `vscode` | VS Code server cache |

Nothing else is mounted. Consequently `~/.claude` (Claude login/settings), `~/.m2` (Maven cache), `~/.finance-app` (app data) and shell history live in the container layer and **are lost when the container is rebuilt** (not when it is merely stopped/restarted).

### Optional: an additional shared or persistent folder

Add to `devcontainer.json`:

```jsonc
"mounts": [
  // Host folder <-> container folder (editable from Windows)
  "source=${localEnv:USERPROFILE}/shared,target=/home/node/shared,type=bind",
  // Container-only persistence across rebuilds (not host-editable)
  "source=claude-home,target=/home/node/.claude,type=volume",
  "source=m2-cache,target=/home/node/.m2,type=volume"
]
```

Notes:
- The host folder must exist before the container starts (`mkdir %USERPROFILE%\shared` on Windows; `mkdir ~/shared` on Linux), or add an `initializeCommand` to create it. On Linux hosts use `${localEnv:HOME}` instead of `USERPROFILE`.
- Named volumes are created root-owned. Fix ownership once with `sudo chown -R node:node /home/node/.claude /home/node/.m2` (put it in `postCreateCommand` if you want it automatic).
- Docs: [Mount types](https://docs.docker.com/engine/storage/bind-mounts/), [Persist data in a dev container](https://code.visualstudio.com/remote/advancedcontainers/persist-bash-history), [Add a local file mount](https://code.visualstudio.com/remote/advancedcontainers/add-local-file-mount).

---

## 7. File permissions and the `node` user

| Location | Owner / mode | Can `node` write? |
|----------|--------------|-------------------|
| `/workspaces/claude-workspace` | root, `drwxrwxrwx` (9p mount) | Yes |
| `/home/node` | node | Yes |
| `/usr/local/share/nvm`, `/usr/local/share/npm-global` | node:nvm / node:npm, setgid | Yes (global `npm i -g` works without sudo) |
| `/usr`, `/etc`, `/opt`, `/usr/local/bin` | root | **No** - use `sudo` |

The `node` user has **passwordless sudo** (`(root) NOPASSWD: ALL`), so any root-owned path can be changed with `sudo` (e.g. `sudo chown -R node:node /some/dir`). This is why the `apt-get` call in `postCreateCommand` works.

For anything permanent (system packages, system folder permissions), put it in the **Dockerfile** as `root` and rebuild the image rather than fixing it by hand in a running container.

---

## 8. Building and publishing the custom image

Rebuild whenever `Dockerfile` changes.

```bash
# Build (Docker)
docker build -t localhost/my-custom-devcontainer .devcontainer/
# or Podman (from WSL / `podman machine ssh`)
podman build -t localhost/my-custom-devcontainer .devcontainer/

# Publish to GitHub Container Registry (needs a PAT with write:packages)
echo "<YOUR_PAT>" | podman login ghcr.io -u smshirish --password-stdin
podman tag  localhost/my-custom-devcontainer:latest ghcr.io/smshirish/my-custom-devcontainer:latest
podman push ghcr.io/smshirish/my-custom-devcontainer:latest
```

Replace `podman` with `docker` if that is your engine. After rebuilding, run **F1 -> Dev Containers: Rebuild Container**.

Docs: [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry), [Podman build](https://docs.podman.io/en/latest/markdown/podman-build.1.html).

---

## 9. Audit findings and known gaps

### Software installed outside the Dockerfile / devcontainer.json
None found. Every installed tool traces to the base image, the Dockerfile, a feature, or `postCreateCommand`. The only things that exist solely inside the running container are **data and config**, not software: `~/.claude`, `~/.claude.json`, `~/.m2`, `~/.finance-app`, `~/.gitconfig`, `~/.bash_history` (see section 6 to persist them).

### Improvements to consider

| # | Finding | Impact | Suggested fix |
|---|---------|--------|---------------|
| 1 | `image: localhost/my-custom-devcontainer` is not portable | Fresh machine fails until image is built/pulled | Use `"image": "ghcr.io/smshirish/my-custom-devcontainer:latest"` (package must be public or you must `docker login`), or replace with `"build": {"dockerfile": "Dockerfile"}` so VS Code builds it automatically |
| 2 | `JAVA_HOME` set in the Dockerfile, in `remoteEnv`, and appended to `~/.bashrc` | Redundant | Keep only the Dockerfile `ENV` |
| 3 | **OPEN** - Two Node installs: base image has Node 20.20.2 (`/usr/local/bin`), the `node` feature adds Node 24.16.0 (nvm, first on PATH). The feature is now pinned to major 24 in `devcontainer.json`, so the active version no longer floats to a future LTS (minor/patch can still move; `devcontainer-lock.json` pins the feature, not the Node build). The unused Node 20 (~99 MB) is still in the image | Wasted space; processes that bypass the nvm PATH would get Node 20 | **Fix in the Docker image (not done yet):** install Node 24 in the `Dockerfile` (or use a base without Node), then remove the `node` feature from `devcontainer.json`. Needs an image rebuild and re-publish, and a check that the `claude-code` feature still finds `npm` |
| 4 | `NET_ADMIN` / `NET_RAW` caps added, but nothing runs `/usr/local/bin/init-firewall.sh` | Unneeded elevated capabilities | Remove `runArgs`, or call the script in `postStartCommand` if you want the egress firewall |
| 5 | `devcontainer.json` contains stray citation markers in comments (`[1]`, `[2]`, `[4]`) | Cosmetic | Delete them |
| 6 | Config and caches not persisted | Re-login to Claude and re-download Maven deps after every rebuild | Add the volume mounts in section 6 |
| 7 | `dockerfilereadme.md` stores publishing notes with a PAT placeholder | Risk of committing a real token | Keep the placeholder only; use a password manager |

### Facts that could not be verified from inside the container
- The exact host container engine product (Docker Desktop vs another Docker-compatible engine). The data root is `/data/docker` and the container was started by the VS Code Dev Containers extension (`REMOTE_CONTAINERS=true`). Podman is documented only for image build/push.
- Whether the GHCR package is public.

---

## 10. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| "Image not found: localhost/my-custom-devcontainer" | Build or pull the image first (section 1, step 2) |
| `Permission denied` writing to a folder as `node` | Folder is root-owned: `sudo chown -R node:node <dir>` or fix in the Dockerfile |
| `npm i -g` permission errors | Check you are `node` (member of `npm` group); don't use `sudo npm` |
| `apt-get` permission denied | Use `sudo apt-get ...` |
| Playwright browser missing libs | `sudo npx playwright install-deps chromium` |
| Changes to Dockerfile not picked up | Rebuild the image, then **Rebuild Container** (use "Rebuild Without Cache" if needed) |
| Files created in container show odd owner on Windows | Expected with 9p mounts; Windows ignores Linux ownership |
| Slow file operations in the workspace | 9p (Windows drive) is slower than native Linux FS; keep `node_modules`/`target` in a named volume if builds are slow |

---

## 11. Reference links

**VS Code / Dev Containers**
- [Developing inside a Container](https://code.visualstudio.com/docs/devcontainers/containers)
- [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)
- [Create a dev container](https://code.visualstudio.com/docs/devcontainers/create-dev-container)
- [Advanced container configuration](https://code.visualstudio.com/remote/advancedcontainers/overview)
- [Add a non-root user / UID mapping](https://code.visualstudio.com/remote/advancedcontainers/add-nonroot-user)

**Dev container spec**
- [containers.dev home](https://containers.dev/)
- [devcontainer.json reference](https://containers.dev/implementors/json_reference/)
- [Available Features](https://containers.dev/features)
- [Microsoft dev container images](https://github.com/devcontainers/images)
- [devcontainers/features (incl. node)](https://github.com/devcontainers/features)

**Claude Code**
- [Claude Code overview](https://docs.anthropic.com/en/docs/claude-code/overview)
- [Claude Code dev container guide](https://docs.anthropic.com/en/docs/claude-code/devcontainer)
- [Anthropic dev container features](https://github.com/anthropics/devcontainer-features)

**Host tooling**
- [Install WSL](https://learn.microsoft.com/windows/wsl/install)
- [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/)
- [Docker Engine for Linux](https://docs.docker.com/engine/install/)
- [Podman installation](https://podman.io/docs/installation)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)

**Project tooling**
- [Playwright](https://playwright.dev/docs/intro)
- [Apache Maven](https://maven.apache.org/guides/getting-started/)
- [OpenJDK 21](https://openjdk.org/projects/jdk/21/)
