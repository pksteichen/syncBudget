// Gemini provider via @google/genai.
//
// Uses structured output (responseSchema) so malformed JSON is impossible.
// Per-call cost at 2.5 Flash: ~$0.001 (input $0.30/1M, output $2.50/1M).
// Flash-Lite is ~5x cheaper; switch via GEMINI_MODEL env var if bake-off
// shows it's competitive.

import { GoogleGenAI } from "@google/genai";
import { RESPONSE_SCHEMA } from "../schema.js";

const DEFAULT_MODEL = "gemini-2.5-flash";

export function isGeminiConfigured() {
  return !!process.env.GEMINI_API_KEY;
}

export async function extractWithGemini({ imageBytes, mimeType, prompt }) {
  if (!isGeminiConfigured()) {
    throw new Error("GEMINI_API_KEY not set — run `cp .env.example .env` and fill it in.");
  }
  const client = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
  const model = process.env.GEMINI_MODEL || DEFAULT_MODEL;

  const start = Date.now();
  const response = await client.models.generateContent({
    model,
    contents: [{
      role: "user",
      parts: [
        { text: prompt },
        { inlineData: { mimeType, data: imageBytes.toString("base64") } },
      ],
    }],
    config: {
      responseMimeType: "application/json",
      responseSchema: RESPONSE_SCHEMA,
      temperature: 0.0,
    },
  });
  const elapsedMs = Date.now() - start;

  const text = response.text;
  if (!text) {
    return { ok: false, error: "Empty response", elapsedMs, raw: response };
  }
  try {
    const parsed = JSON.parse(text);
    return { ok: true, extracted: parsed, elapsedMs, model };
  } catch (e) {
    return { ok: false, error: `JSON parse failed: ${e.message}`, raw: text, elapsedMs };
  }
}
