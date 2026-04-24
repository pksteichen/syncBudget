#!/usr/bin/env node
// Fetches current deployed Firestore + Storage + RTDB rules from
// Firebase Rules API and prints them to stdout / writes them to
// firestore.rules, storage.rules, database.rules.json in the repo root.
//
// Run from inside /data/data/.../dailyBudget: `node tools/fetch-rules.js`

const fs = require('fs');
const path = require('path');
const admin = require('/data/data/com.termux/files/home/dailyBudget/functions/node_modules/firebase-admin');
const { GoogleAuth } = require('/data/data/com.termux/files/home/dailyBudget/functions/node_modules/google-auth-library');

const SA_PATH = '/data/data/com.termux/files/home/dailyBudget/app/src/debug/assets/service-account.json';
const PROJECT_ID = 'sync-23ce9';
const OUT_DIR = '/data/data/com.termux/files/home/dailyBudget';

async function getAccessToken() {
  const sa = JSON.parse(fs.readFileSync(SA_PATH, 'utf-8'));
  const auth = new GoogleAuth({
    credentials: sa,
    scopes: ['https://www.googleapis.com/auth/firebase.readonly'],
  });
  const client = await auth.getClient();
  const token = await client.getAccessToken();
  return token.token;
}

async function fetchRuleset(token, releaseName) {
  // Step 1: get current release → ruleset name
  const rel = await fetch(
    `https://firebaserules.googleapis.com/v1/projects/${PROJECT_ID}/releases/${releaseName}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (!rel.ok) {
    const err = await rel.text();
    throw new Error(`release ${releaseName}: ${rel.status} ${err}`);
  }
  const release = await rel.json();
  const rulesetName = release.rulesetName;

  // Step 2: get ruleset source
  const rs = await fetch(
    `https://firebaserules.googleapis.com/v1/${rulesetName}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (!rs.ok) {
    const err = await rs.text();
    throw new Error(`ruleset ${rulesetName}: ${rs.status} ${err}`);
  }
  const ruleset = await rs.json();
  return ruleset.source.files.map(f => ({ name: f.name, content: f.content }));
}

async function main() {
  const token = await getAccessToken();

  const targets = [
    { release: 'cloud.firestore', outFile: 'firestore.rules' },
    { release: 'firebase.storage/sync-23ce9.firebasestorage.app', outFile: 'storage.rules' },
  ];

  for (const t of targets) {
    try {
      const files = await fetchRuleset(token, t.release);
      for (const f of files) {
        const outPath = path.join(OUT_DIR, t.outFile);
        fs.writeFileSync(outPath, f.content);
        console.log(`✔ ${t.release} → ${outPath} (${f.content.length} bytes)`);
      }
    } catch (e) {
      console.warn(`✘ ${t.release}: ${e.message}`);
    }
  }

  // RTDB rules use a different API and need the firebase.database scope
  try {
    const sa = JSON.parse(fs.readFileSync(SA_PATH, 'utf-8'));
    const dbAuth = new GoogleAuth({
      credentials: sa,
      scopes: [
        'https://www.googleapis.com/auth/firebase.database',
        'https://www.googleapis.com/auth/userinfo.email',
      ],
    });
    const dbToken = (await (await dbAuth.getClient()).getAccessToken()).token;
    const dbName = 'sync-23ce9-default-rtdb';
    const res = await fetch(
      `https://${dbName}.firebaseio.com/.settings/rules.json`,
      { headers: { Authorization: `Bearer ${dbToken}` } }
    );
    if (res.ok) {
      const rules = await res.text();
      fs.writeFileSync(path.join(OUT_DIR, 'database.rules.json'), rules);
      console.log(`✔ RTDB → ${path.join(OUT_DIR, 'database.rules.json')} (${rules.length} bytes)`);
    } else {
      console.warn(`✘ RTDB: ${res.status} ${await res.text()}`);
    }
  } catch (e) {
    console.warn(`✘ RTDB: ${e.message}`);
  }
}

main().catch(e => { console.error(e); process.exit(1); });
