/**
 * 从根目录 .env 文件生成 TV 端配置
 * 运行: node scripts/generate-config.js
 */

const fs = require('fs');
const path = require('path');

// 读取根目录的 .env 文件
const envPath = path.resolve(__dirname, '../../.env');
const outputPath = path.resolve(__dirname, '../src/config/env.js');

function parseEnvFile(filePath) {
  if (!fs.existsSync(filePath)) {
    console.warn(`⚠️  .env 文件不存在: ${filePath}`);
    console.warn('使用默认配置');
    return {};
  }

  const content = fs.readFileSync(filePath, 'utf-8');
  const env = {};

  content.split('\n').forEach(line => {
    line = line.trim();
    // 跳过注释和空行
    if (!line || line.startsWith('#')) return;

    const [key, ...valueParts] = line.split('=');
    if (key && valueParts.length > 0) {
      env[key.trim()] = valueParts.join('=').trim();
    }
  });

  return env;
}

function generateConfig() {
  const env = parseEnvFile(envPath);

  const config = {
    TV_API_HOST: env.TV_API_HOST || ''
  };

  // 检查必需配置
  if (!config.TV_API_HOST) {
    console.error('❌ 错误: TV_API_HOST 未配置');
    console.error('请在根目录 .env 文件中设置 TV_API_HOST');
    process.exit(1);
  }

  const output = `/**
 * 环境配置文件
 * 此文件由 scripts/generate-config.js 自动生成
 * 请勿手动修改，修改请编辑根目录的 .env 文件
 */

export const ENV_CONFIG = ${JSON.stringify(config, null, 2)};
`;

  // 确保目录存在
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
