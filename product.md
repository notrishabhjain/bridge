---
inclusion: always
---

# Sentinel AI Bridge — Product Overview

## What This Is

Sentinel AI Bridge is a **local, AI-powered Android middleware** that converts phone call transcripts into structured tasks and actions. It runs entirely on-device. No internet required. No cloud. No telemetry.

## Target User

RJ — Project Manager using a Redmi Turbo 5 with HyperOS 2.x. Makes frequent work calls in Hindi. Wants tasks and commitments extracted automatically after every call without manual effort.

## Core Value

Current phone automation tools can detect "call ended" but cannot reason. Sentinel adds a local reasoning layer that extracts tasks, commitments, deadlines, and follow-ups from call transcripts and returns them as structured JSON to MacroDroid.

## MVP Scope

One pipeline: **recorded call → transcript → local AI → structured JSON → MacroDroid**

## What It Is Not

- Not a chatbot
- Not a transcription service (Xiaomi HyperAI handles transcription)
- Not a MacroDroid replacement (MacroDroid triggers and acts; Sentinel reasons)
- Not a cloud service

## Future Vision

The same architecture will handle WhatsApp, SMS, email, clipboard, voice notes, and documents — all as plugins. The core pipeline never changes.

## Non-Negotiables

- **Privacy absolute**: no network in MVP, no analytics, no crash reporting
- **Local first**: everything runs on device
- **Event-driven**: no polling, no Thread.sleep()
- **Resilient**: process kills are recoverable via WorkManager + Room
