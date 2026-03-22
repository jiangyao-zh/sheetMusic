function naturalCompare(a, b) {
  return String(a).localeCompare(String(b), 'en', { numeric: true, sensitivity: 'base' });
}

export function getFlatImagesFromStatic() {
  let imageContext = null;
  try {
    imageContext = require.context('../../static/scores', true, /\.(png|jpe?g|webp)$/i);
  } catch (err) {
    return [];
  }

  return imageContext
    .keys()
    .sort(naturalCompare)
    .map((key) => {
      const relative = key.replace(/^\.\//, '');
      return {
        id: relative,
        title: relative,
        src: imageContext(key),
        type: 'static'
      };
    });
}
