// Anthropic provider via @anthropic-ai/sdk.
//
// Anthropic doesn't enforce a server-side JSON schema, so we instruct the
// model to return ONLY JSON matching our schema and parse manually. Tool-use
// mode can emulate structured output, but for a side-by-side bake-off it's
// more apples-to-apples to use the same "return JSON" pattern for both.

import Anthropic from "@anthropic-ai/sdk";
import { RESPONSE_SCHEMA } from "../schema.js";

const DEFAULT_MODEL = "claude-haiku-4-5-20251001";

export function isAnthropicConfigured() {
  return !!process.env.ANTHROPIC_API_KEY;
}

export async function extractWithAnthropic({ imageBytes, mimeType, prompt }) {
  if (!isAnthropicConfigured()) {
    throw new Error("ANTHROPIC_API_KEY not set — add it to .env to enable this provider.");
  }
  const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });
  const model = process.env.ANTHROPIC_MODEL || DEFAULT_MODEL;

  const schemaJson = JSON.stringify(RESPONSE_SCHEMA, null, 2);
  const fullPrompt = `${prompt}

Return ONLY valid JSON matching this schema (no markdown fences, no commentary):

${schemaJson}`;

  const start = Date.now();
  const response = await client.messages.create({
    model,
    max_tokens: 1024,
    temperature: 0.0,
    messages: [{
      role: "user",
      content: [
        { type: "image", source: { type: "base64", media_type: mimeType, data: imageBytes.toString("base64") } },
        { type: "text", text: fullPrompt },
      ],
    }],
  });
  const elapsedMs = Date.now() - start;

  const textBlock = response.content.find(b => b.type === "text");
  if (!textBlock) {
    return { ok: false, error: "No text block in response", elapsedMs, raw: response };
  }
  const text = textBlock.text.trim()
    .replace(/^```json\s*/i, "")
    .replace(/\s*```$/, "");
  try {
    const parsed = JSON.parse(text);
    return { ok: true, extracted: parsed, elapsedMs, model };
  } catch (e) {
    return { ok: false, error: `JSON parse failed: ${e.message}`, raw: text, elapsedMs };
  }
}
