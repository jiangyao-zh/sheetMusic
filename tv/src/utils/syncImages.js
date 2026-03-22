/**
 * 图片同步工具
 * 从服务器下载乐谱原图到本地目录，供列表和详情页读取
 * 
 * 同步流程：
 * 1. 从 API 获取乐谱列表，去除 thumb_ 前缀获取原图地址
 * 2. 删除本地已有的同步数据
 * 3. 逐张下载图片到 _doc/sheet_images/ 目录
 * 4. 保存同步元数据到 storage
 * 
 * 注意：_doc/ 是 APP 私有目录，无需额外存储权限
 */

import { getApiHost, API_ENDPOINTS } from '@/src/config/api';

const SYNC_DIR = '_doc/sheet_images/';
const SYNC_META_KEY = 'sheet_sync_meta';

/**
 * 去除URL中的 thumb_ 前缀，获取原图地址
 */
function removeThumbPrefix(url) {
  return url.replace(/\/thumb_/g, '/');
}

/**
 * 从 API 获取乐谱列表
 */
function fetchSheetListFromApi() {
  const baseUrl = getApiHost();
  const url = `${baseUrl}${API_ENDPOINTS.SHEETS_EXTERNAL}`;

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: 'GET',
      timeout: 15000,
      success: (res) => {
        if (res.statusCode === 200 && res.data && res.data.code === 200 && Array.isArray(res.data.data)) {
          const items = res.data.data.map((item) => ({
            id: `sheet-${item.id}`,
            title: item.title || `乐谱 ${item.id}`,
            remoteUrl: removeThumbPrefix(`${baseUrl}${item.thumbUrl}`),
            uploadTime: item.uploadTime,
            bpm: item.bpm || 80
          }));
          resolve(items);
        } else {
          reject(new Error('[Sync] 接口返回格式异常'));
        }
      },
      fail: (err) => reject(err)
    });
  });
}

/**
 * 确保同步目录存在
 */
function ensureSyncDir() {
  // #ifdef APP-PLUS
  return new Promise((resolve, reject) => {
    plus.io.resolveLocalFileSystemURL('_doc/', (docEntry) => {
      docEntry.getDirectory('sheet_images', { create: true }, (dirEntry) => {
        console.log('[Sync] 同步目录已就绪:', dirEntry.fullPath);
        resolve(dirEntry);
      }, reject);
    }, reject);
  });
  // #endif
  // #ifndef APP-PLUS
  return Promise.resolve(null);
  // #endif
}

/**
 * 清空同步目录中的所有文件
 */
function clearSyncDir() {
  // #ifdef APP-PLUS
  return new Promise((resolve) => {
    plus.io.resolveLocalFileSystemURL(SYNC_DIR, (dirEntry) => {
      const reader = dirEntry.createReader();
      reader.readEntries((entries) => {
        if (!entries.length) { resolve(); return; }
        let pending = entries.length;
        const done = () => { pending--; if (pending <= 0) resolve(); };
        entries.forEach((entry) => {
          if (entry.isFile) {
            entry.remove(done, done);
          } else {
            done();
          }
        });
      }, () => resolve());
    }, () => resolve());
  });
  // #endif
  // #ifndef APP-PLUS
  return Promise.resolve();
  // #endif
}

/**
 * 下载单张图片到本地目录
 */
function downloadOneImage(remoteUrl, filename) {
  // #ifdef APP-PLUS
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: remoteUrl,
      success: (res) => {
        if (res.statusCode === 200 && res.tempFilePath) {
          plus.io.resolveLocalFileSystemURL(res.tempFilePath, (tempEntry) => {
            plus.io.resolveLocalFileSystemURL(SYNC_DIR, (dirEntry) => {
              tempEntry.moveTo(dirEntry, filename, (newEntry) => {
                resolve(newEntry.toLocalURL());
              }, (err) => reject(err));
            }, (err) => reject(err));
          }, (err) => reject(err));
        } else {
          reject(new Error(`HTTP ${res.statusCode}`));
        }
      },
      fail: (err) => reject(err)
    });
  });
  // #endif
  // #ifndef APP-PLUS
  // H5 环境无法下载到本地文件系统，直接使用远程URL
  return Promise.resolve(remoteUrl);
  // #endif
}

/**
 * 执行完整同步流程
 * @param {Function} onProgress - 进度回调 ({ current, total, title })
 * @returns {Promise<Array>} 同步后的图片列表
 */
export async function syncAllImages(onProgress) {
  console.log('[Sync] 开始同步...');

  // 1. 获取远程列表
  const items = await fetchSheetListFromApi();
  if (!items.length) {
    console.warn('[Sync] 远程列表为空');
    return [];
  }
  console.log(`[Sync] 获取到 ${items.length} 张图片`);

  // 2. 清空旧数据，确保目录存在
  await ensureSyncDir();
  await clearSyncDir();
  console.log('[Sync] 已清空旧数据');

  // 3. 逐个下载
  const result = [];
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    // 从URL中提取文件名
    const urlParts = item.remoteUrl.split('/');
    const filename = urlParts[urlParts.length - 1] || `${item.id}.jpg`;

    if (onProgress) {
      onProgress({ current: i + 1, total: items.length, title: item.title });
    }

    try {
      const localSrc = await downloadOneImage(item.remoteUrl, filename);
      result.push({
        id: item.id,
        title: item.title,
        src: localSrc,
        uploadTime: item.uploadTime,
        bpm: item.bpm,
        type: 'local'
      });
      console.log(`[Sync] (${i + 1}/${items.length}) ${item.title} ✓`);
    } catch (e) {
      console.error(`[Sync] (${i + 1}/${items.length}) ${item.title} ✗`, e);
      // 下载失败回退到远程URL
      result.push({
        id: item.id,
        title: item.title,
        src: item.remoteUrl,
        uploadTime: item.uploadTime,
        bpm: item.bpm,
        type: 'remote'
      });
    }
  }

  // 4. 保存同步元数据
  try {
    uni.setStorageSync(SYNC_META_KEY, JSON.stringify({
      timestamp: Date.now(),
      count: result.length,
      images: result
    }));
    console.log(`[Sync] 同步完成，共 ${result.length} 张`);
  } catch (e) {
    console.error('[Sync] 保存元数据失败:', e);
  }

  return result;
}

/**
 * 获取本地已同步的图片列表
 * @returns {Array|null} 图片列表，未同步时返回 null
 */
export function getLocalSyncedImages() {
  try {
    const raw = uni.getStorageSync(SYNC_META_KEY);
    if (!raw) return null;
    const meta = JSON.parse(raw);
    return meta && Array.isArray(meta.images) ? meta.images : null;
  } catch (e) {
    return null;
  }
}

/**
 * 是否需要首次同步（本地无同步数据）
 */
export function needsFirstSync() {
  return !getLocalSyncedImages();
}
