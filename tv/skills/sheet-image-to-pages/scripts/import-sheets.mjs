#!/usr/bin/env node
import { spawn } from "node:child_process";
import path from "node:path";

const args = process.argv.slice(2);
const projectRoot = process.cwd();
const scriptPath = path.resolve(projectRoot, "tools/generate-sheets.mjs");

const child = spawn(process.execPath, [scriptPath, ...args], {
  stdio: "inherit",
  cwd: projectRoot
});

child.on("exit", (code) => process.exit(code ?? 0));
