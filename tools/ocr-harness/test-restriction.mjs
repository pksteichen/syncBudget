// One-shot test: does the harness key still authorize?
import { GoogleGenAI } from "@google/genai";
import fs from "fs";

const env = fs.readFileSync("/data/data/com.termux/files/home/dailyBudget/tools/ocr-harness/.env", "utf8");
const apiKey = env.match(/^GEMINI_API_KEY=(.+)$/m)[1].trim();

const client = new GoogleGenAI({ apiKey });
try {
  const start = Date.now();
  const response = await client.models.generateContent({
    model: "gemini-2.5-flash-lite",
    contents: [{ role: "user", parts: [{ text: "Say OK." }] }],
    config: { temperature: 0 },
  });
  console.log(`SUCCESS in ${Date.now() - start}ms: ${response.text?.slice(0, 60)}`);
} catch (e) {
  console.log(`FAIL: ${e.message?.slice(0, 400)}`);
}
