---
description: Rules for the bpl-order-engine staging container. Always check this before writing start/stop/status/log code for the Bpl engine, or before running any command that could reach staging.
---

The BPL Order Engine at 180.210.129.233 is a live staging container also used
for JMeter integration tests. Never wire real start/stop/docker commands
against it. All engine control in this project uses an in-memory mock state
machine (RUNNING/STOPPED/ERROR) unless the person you're working with has
explicitly said, in this session, that it's safe to touch the real container.
If a task seems to require touching it, stop and ask first.