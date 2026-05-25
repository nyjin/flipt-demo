# syntax=docker/dockerfile:1
#
# Flipt v2 server image. Bakes the flags in as the LOCAL git repo for the
# `local` environment. The dev/staging/prod environments instead clone their
# release branches from the remote repo at runtime (see config.yml), so they are
# NOT baked here.
#
# NOTE: the build context is the repository ROOT (see the flipt service in
# docker-compose.yml), because the declarative flags live in the top-level
# `config/` directory — outside this app folder.
#
# Flipt v2's `local` storage backend reads flags from a *git repository*. The
# official Flipt image ships without a git binary, so we use a small seed stage
# to turn the declarative flag files into a committed repo, then copy the result
# into the Flipt image. The repo is laid out as /flags/config/<env>/... so the
# `local` environment (directory: config/dev) resolves the same way the remote
# environments do.

# ---- Stage 1: build a git repo out of the declarative flag files ----
FROM alpine/git:latest AS seed
WORKDIR /flags
COPY config/ /flags/config/
RUN git init -b main -q \
 && git config user.email "flipt@example.com" \
 && git config user.name "flipt-demo-seed" \
 && git add . \
 && git commit -qm "seed feature flags"

# ---- Stage 2: Flipt v2 server ----
FROM docker.flipt.io/flipt/flipt:v2

# Server config (Flipt auto-loads /etc/flipt/config/default.yml)
COPY apps/flipt/config/config.yml /etc/flipt/config/default.yml

# Flag repository — chown to the non-root `flipt` user (uid 100) so the UI can
# also commit live changes back into the repo.
COPY --from=seed --chown=100:1000 /flags /flags

EXPOSE 8080 9000
