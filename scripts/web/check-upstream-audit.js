#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const repoRoot = path.resolve(__dirname, '..', '..');
const auditPath = path.join(repoRoot, 'ai_stuffs', 'upstream_diff_audit.csv');
const upstreamRef = process.env.UPSTREAM_REF || 'upstream/master';

const protectedPrefixes = [
  'web/',
  'scripts/web/',
  '.devcontainer/',
  'tmp/',
];

const protectedExact = new Set([
  '.github/actions/web-test-env/action.yml',
  '.github/workflows/web-build.yml',
  '.github/workflows/web-war-deep-nightly.yml',
  'buildSrc/src/main/kotlin/WebBuildTasks.kt',
  'docker-compose.web.yml',
]);

function runGit(args) {
  return execFileSync('git', args, {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let inQuotes = false;

  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        field += ch;
      }
      continue;
    }

    if (ch === '"') {
      inQuotes = true;
      continue;
    }
    if (ch === ',') {
      row.push(field);
      field = '';
      continue;
    }
    if (ch === '\n') {
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
      continue;
    }
    if (ch !== '\r') {
      field += ch;
    }
  }

  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }

  if (rows.length === 0) return [];
  const headers = rows[0];
  return rows.slice(1).filter((values) => values.length > 1).map((values) => {
    const entry = {};
    headers.forEach((header, index) => {
      entry[header] = values[index] || '';
    });
    return entry;
  });
}

function classifyBucket(filePath) {
  if (filePath === 'AGENTS.md' || filePath.startsWith('ai_stuffs/')) return 'meta_user';
  if (protectedExact.has(filePath)) return 'protected_web';
  if (protectedPrefixes.some((prefix) => filePath.startsWith(prefix))) return 'protected_web';
  return 'audited_non_web';
}

function normalizeDecision(row) {
  const explicit = String(row.AlignmentDecision || '').trim().toLowerCase();
  if (explicit === 'keep' || explicit === 'compose' || explicit === 'revert') return explicit;

  const legacy = String(row.Decision || '').trim().toLowerCase();
  if (legacy === 'keep' || legacy === 'keep_user_requested') return 'keep';
  if (legacy === 'partial_revert') return 'compose';
  if (legacy === 'revert' || legacy === 'drop') return 'revert';
  return '';
}

function main() {
  let diffNames;
  try {
    diffNames = runGit(['diff', '--name-only', `${upstreamRef}...HEAD`]);
  } catch (err) {
    const stderr = String(err.stderr || err.message || err);
    console.error(`[audit] unable to diff against ${upstreamRef}: ${stderr.trim()}`);
    process.exit(1);
  }

  const changedFiles = diffNames.split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
  const auditRows = parseCsv(fs.readFileSync(auditPath, 'utf8'));
  const auditByPath = new Map(auditRows.map((row) => [row.Path, row]));

  const requiredRows = [];
  const missingRows = [];
  const ambiguousRows = [];
  const noteGaps = [];

  for (const filePath of changedFiles) {
    if (classifyBucket(filePath) !== 'audited_non_web') continue;
    requiredRows.push(filePath);
    const row = auditByPath.get(filePath);
    if (!row) {
      missingRows.push(filePath);
      continue;
    }
    const decision = normalizeDecision(row);
    if (!decision) {
      ambiguousRows.push(filePath);
      continue;
    }
    const note = String(row.AlignmentNote || row.Reason || '').trim();
    if (!note) noteGaps.push(filePath);
  }

  console.log(`[audit] upstream ref: ${upstreamRef}`);
  console.log(`[audit] changed files vs upstream: ${changedFiles.length}`);
  console.log(`[audit] audited non-web files: ${requiredRows.length}`);

  if (missingRows.length > 0) {
    console.error('[audit] missing CSV rows:');
    missingRows.forEach((filePath) => console.error(`  - ${filePath}`));
  }
  if (ambiguousRows.length > 0) {
    console.error('[audit] missing keep/compose/revert decision:');
    ambiguousRows.forEach((filePath) => console.error(`  - ${filePath}`));
  }
  if (noteGaps.length > 0) {
    console.error('[audit] missing audit note:');
    noteGaps.forEach((filePath) => console.error(`  - ${filePath}`));
  }

  if (missingRows.length > 0 || ambiguousRows.length > 0 || noteGaps.length > 0) {
    process.exit(1);
  }

  console.log('[audit] non-web diff audit is complete.');
}

main();
