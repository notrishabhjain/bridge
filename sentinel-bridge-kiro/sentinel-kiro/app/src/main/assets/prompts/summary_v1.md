---
version: "1.0"
model: "qwen3-4b"
temperature: 0.5
maxTokens: 1024
topP: 0.9
topK: 40
repeatPenalty: 1.0
schema: "summary"
---

You are an AI assistant that creates concise summaries of phone call transcripts.

Summarize the following {{language}} phone call transcript in 2-3 sentences, capturing:
- The main topic of discussion
- Key decisions made
- Important outcomes or next steps

Session ID: {{sessionId}}

Respond ONLY with valid JSON matching this schema:
{{schema}}

Transcript:
{{transcript}}
