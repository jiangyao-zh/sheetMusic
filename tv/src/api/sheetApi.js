/**
 * 乐谱 API 工具类
 * 调用 manager 后端接口获取乐谱数据
 */

import { getApiHost, API_ENDPOINTS } from '@/src/config/api';

/**
 * 获取乐谱列表（外部接口，无需鉴权）
 * @returns {Promise<Array>} 乐谱列表
 */
export async function fetchSheetList() {
  const baseUrl = getApiHost();
  const url = `${baseUrl}${API_ENDPOINTS.SHEETS_EXTERNAL}`;

  try {
    const response = await new Promise((resolve, reject) => {
      uni.request({
        url: url,
        method: 'GET',
        timeout: 10000,
        success: (res) => {
          if (res.statusCode === 200) {
            resolve(res.data);
          } else {
            reject(new Error(`HTTP ${res.statusCode}`));
          }
        },
        fail: (err) => {
          reject(err);
        }
      });
    });

    // 检查响应格式
    if (response && response.code === 200 && Array.isArray(response.data)) {
      // 转换为 TV 端需要的格式
      return response.data.map((item) => ({
        id: `sheet-${item.id}`,
        title: item.title || `乐谱 ${item.id}`,
        // 后端已不压缩图片，thumbUrl 即为原图，直接使用
        src: `${baseUrl}${item.thumbUrl}`,
        uploadTime: item.uploadTime,
        type: 'remote'
      }));
    }

    console.warn('[SheetAPI] 接口返回格式异常:', response);
    return [];
  } catch (error) {
    console.error('[SheetAPI] 获取乐谱列表失败:', error);
    // 返回空数组，不影响本地乐谱显示
    return [];
  }
}

/**
 * 获取乐谱详情（通过 ID）
 * @param {string} sheetId - 乐谱 ID (格式: sheet-123)
 * @returns {Promise<Object|null>} 乐谱详情
 */
export async function fetchSheetDetail(sheetId) {
  try {
    const list = await fetchSheetList();
    return list.find((item) => item.id === sheetId) || null;
  } catch (error) {
    console.error('[SheetAPI] 获取乐谱详情失败:', error);
    return null;
  }
}

/**
 * 合并远程和本地乐谱列表
 * @param {Array} localSheets - 本地乐谱列表
 * @returns {Promise<Array>} 合并后的乐谱列表
 */
export async function getMergedSheetList(localSheets = []) {
  try {
    const remoteSheets = await fetchSheetList();
    // 远程乐谱优先显示
    return remoteSheets.concat(localSheets);
  } catch (error) {
    console.error('[SheetAPI] 合并乐谱列表失败:', error);
    // 如果远程获取失败，只返回本地乐谱
    return localSheets;
  }
}
