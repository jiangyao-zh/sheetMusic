/**
 * API 配置文件
 * 统一管理后端接口地址
 * 配置从根目录 .env 文件读取，通过 scripts/generate-config.js 生成
 */

import { ENV_CONFIG } from './env';

// API 地址（从 .env 读取）
export const API_HOST = ENV_CONFIG.TV_API_HOST;

/**
 * 获取 API 地址
 * @returns {string} API 基础地址
 */
export function getApiHost() {
  return API_HOST;
}

/**
 * API 端点配置
 */
export const API_ENDPOINTS = {
  // 乐谱相关接口
  SHEETS_EXTERNAL: '/api/sheets/external',  // 获取乐谱列表（公开接口）
  SHEETS_LIST: '/api/sheets',                // 获取乐谱列表（需要鉴权）
  SHEETS_UPLOAD: '/api/sheets',              // 上传乐谱
  SHEETS_UPDATE: '/api/sheets/:id',          // 更新乐谱
  SHEETS_DELETE: '/api/sheets/:id',          // 删除乐谱
  SHEETS_SORT: '/api/sheets/:id/sort',       // 修改排序
  
  // 认证相关接口
  AUTH_LOGIN: '/api/auth/login'              // 登录
};
