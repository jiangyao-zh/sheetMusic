/**
 * 从 .env 生成 TV 端 src/config/env.js
 * 运行: npm run config:generate
 *
 * 查找顺序：
 * 1) tv/.env
 * 2) 仓库根目录 .env
 */

const fs = require('fs');
const path = require('path');

const tvEnvPath = path.resolve(__dirname, '../.env');
const rootEnvPath = path.resolve(__dirname, '../../.env');
const outputPath = path.resolve(__dirname, '../src/config/env.js');

function parseEnvFile(filePath) {
  if (!fs.existsSync(filePath)) {
    return null;
  }

  const content = fs.readFileSync(filePath, 'utf-8');
  const env = {};

  content.split('\n').forEach((line) => {
    line = line.trim();
    if (!line || line.startsWith('#')) return;

    const [key, ...valueParts] = line.split('=');
    if (key && valueParts.length > 0) {
      env[key.trim()] = valueParts.join('=').trim();
    }
  });

  return env;
}

function generateConfig() {
  let envPath = '';
  let env = parseEnvFile(tvEnvPath);
  if (env) {
    envPath = tvEnvPath;
  } else {
    env = parseEnvFile(rootEnvPath);
    if (env) envPath = rootEnvPath;
  }

  if (!env) {
    console.error('❌ 未找到 .env');
    console.error(`请创建: ${tvEnvPath}`);
    console.error(`或: ${rootEnvPath}`);
    process.exit(1);
  }

  console.log(`读取配置: ${envPath}`);

  const config = {
    TV_API_HOST: (env.TV_API_HOST || '').replace(/\/+$/, ''),
  };

  if (!config.TV_API_HOST) {
    console.error('❌ 错误: TV_API_HOST 未配置');
    console.error(`请在 ${envPath} 中设置 TV_API_HOST`);
    process.exit(1);
  }

  const output = `/**
 * 环境配置文件
 * 此文件由 scripts/generate-config.js 自动生成
 * 请勿手动修改；修改请编辑 tv/.env 或仓库根目录 .env，再执行 npm run config:generate
 */

export const ENV_CONFIG = ${JSON.stringify(config, null, 2)};
`;

  const dir = path.dirname(outputPath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }

  fs.writeFileSync(outputPath, output, 'utf-8');
  console.log('✅ 配置文件生成成功:', outputPath);
  console.log('配置内容:', config);
}

try {
  generateConfig();
} catch (error) {
  console.error('❌ 生成配置文件失败:', error);
  process.exit(1);
}
