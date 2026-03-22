#!/usr/bin/env node
import { promises as fs } from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";

const IMAGE_EXTS = new Set([".jpg", ".jpeg", ".png", ".webp"]);

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) continue;
    const key = token.slice(2);
    const value = argv[i + 1] && !argv[i + 1].startsWith("--") ? argv[++i] : true;
    args[key] = value;
  }
  return args;
}

function toId(name) {
  const raw = String(name)
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  const hash = createHash("sha1").update(String(name)).digest("hex").slice(0, 8);
  const base = raw || "score";
  return `${base}-${hash}`;
}

function naturalCompare(a, b) {
  return a.localeCompare(b, "en", { numeric: true, sensitivity: "base" });
}

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function rmDir(dir) {
  await fs.rm(dir, { recursive: true, force: true });
}

async function listDir(dir) {
  try {
    return await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const projectRoot = process.cwd();
  const inputDir = path.resolve(projectRoot, String(args.input || "sheet-source"));
  const mode = String(args.mode || "direct"); // direct | copy
  const staticDir = path.resolve(projectRoot, String(args.static || "static/scores"));
  const dataDir = path.resolve(projectRoot, String(args.data || "src/data"));
  const sheetsDir = path.resolve(dataDir, "sheets");
  const clearOutput = args.clear !== "false";

  const topEntries = (await listDir(inputDir)).sort((a, b) => naturalCompare(a.name, b.name));
  if (!topEntries.length) {
    console.log(`[generate-sheets] 输入目录为空: ${inputDir}`);
    return;
  }

  if (clearOutput) {
    if (mode === "copy") {
      await rmDir(staticDir);
    }
    await rmDir(sheetsDir);
  }
  if (mode === "copy") {
    await ensureDir(staticDir);
  }
  await ensureDir(sheetsDir);
  await ensureDir(dataDir);

  const scores = [];
  const scoreFolders = topEntries.filter((e) => e.isDirectory());
  const topImages = topEntries
    .filter((e) => e.isFile())
    .map((e) => e.name)
    .filter((name) => IMAGE_EXTS.has(path.extname(name).toLowerCase()))
    .sort(naturalCompare);

  if (!scoreFolders.length && !topImages.length) {
    console.log("[generate-sheets] 未找到可用图片。");
    return;
  }

  for (const folder of scoreFolders) {
    const title = folder.name;
    const id = toId(folder.name);
    const sourceFolder = path.join(inputDir, folder.name);
    const outputFolder = path.join(staticDir, id);
    if (mode === "copy") {
      await ensureDir(outputFolder);
    }

    const files = (await listDir(sourceFolder))
      .filter((e) => e.isFile())
      .map((e) => e.name)
      .filter((name) => IMAGE_EXTS.has(path.extname(name).toLowerCase()))
      .sort(naturalCompare);

    if (!files.length) {
      console.log(`[generate-sheets] 跳过空目录: ${sourceFolder}`);
      continue;
    }

    const pages = [];
    for (let i = 0; i < files.length; i += 1) {
      const sourceName = files[i];
      const ext = path.extname(sourceName).toLowerCase();
      const outputName = `${String(i + 1).padStart(3, "0")}${ext}`;
      const srcPath = path.join(sourceFolder, sourceName);
      const srcStat = await fs.stat(srcPath);
      const version = srcStat.mtimeMs.toString(36);
      if (mode === "copy") {
        const destPath = path.join(outputFolder, outputName);
        // 默认保留原图，不进行压缩，确保 TV 端细节清晰。
        await fs.copyFile(srcPath, destPath);
        pages.push(`/static/scores/${id}/${outputName}?v=${version}`);
      } else {
        const rel = path.relative(projectRoot, srcPath);
        const encoded = rel.split(path.sep).map((segment) => encodeURIComponent(segment)).join("/");
        pages.push(`/${encoded}?v=${version}`);
      }
    }

    const score = {
      id,
      title,
      cover: pages[0],
      totalPages: pages.length,
      pages
    };

    scores.push(score);
    await fs.writeFile(
      path.join(sheetsDir, `${id}.json`),
      JSON.stringify(score, null, 2),
      "utf8"
    );
  }

  if (topImages.length) {
    const id = "sheet-source-root";
    const title = "sheet-source";
    const outputFolder = path.join(staticDir, id);
    if (mode === "copy") {
      await ensureDir(outputFolder);
    }
    const pages = [];
    for (let i = 0; i < topImages.length; i += 1) {
      const sourceName = topImages[i];
      const ext = path.extname(sourceName).toLowerCase();
      const outputName = `${String(i + 1).padStart(3, "0")}${ext}`;
      const srcPath = path.join(inputDir, sourceName);
      const srcStat = await fs.stat(srcPath);
      const version = srcStat.mtimeMs.toString(36);
      if (mode === "copy") {
        const destPath = path.join(outputFolder, outputName);
        await fs.copyFile(srcPath, destPath);
        pages.push(`/static/scores/${id}/${outputName}?v=${version}`);
      } else {
        const rel = path.relative(projectRoot, srcPath);
        const encoded = rel.split(path.sep).map((segment) => encodeURIComponent(segment)).join("/");
        pages.push(`/${encoded}?v=${version}`);
      }
    }
    const score = {
      id,
      title,
      cover: pages[0],
      totalPages: pages.length,
      pages
    };
    scores.push(score);
    await fs.writeFile(
      path.join(sheetsDir, `${id}.json`),
      JSON.stringify(score, null, 2),
      "utf8"
    );
  }

  scores.sort((a, b) => naturalCompare(a.title, b.title));
  await fs.writeFile(path.join(dataDir, "scores.json"), JSON.stringify(scores, null, 2), "utf8");
  console.log(`[generate-sheets] 生成完成，共 ${scores.length} 首乐谱，模式: ${mode}`);
}

main().catch((err) => {
  console.error("[generate-sheets] 失败:", err);
  process.exit(1);
});
