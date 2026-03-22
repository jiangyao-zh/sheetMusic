function naturalCompare(a, b) {
  return String(a).localeCompare(String(b), "en", {
    numeric: true,
    sensitivity: "base"
  });
}

function buildScoresFromStatic() {
  let imageContext = null;
  try {
    imageContext = require.context("../../static/scores", true, /\.(png|jpe?g|webp)$/i);
  } catch (err) {
    return [];
  }

  const grouped = {};
  const keys = imageContext.keys().sort(naturalCompare);

  keys.forEach((key) => {
    const relative = key.replace(/^\.\//, "");
    const parts = relative.split("/");
    if (!parts.length) return;

    const group = parts.length > 1 ? parts[0] : "default";
    if (!grouped[group]) {
      grouped[group] = {
        id: group,
        title: group,
        pages: []
      };
    }
    grouped[group].pages.push(imageContext(key));
  });

  return Object.values(grouped)
    .map((item) => ({
      id: item.id,
      title: item.title,
      cover: item.pages[0] || "",
      totalPages: item.pages.length,
      pages: item.pages.sort(naturalCompare)
    }))
    .filter((item) => item.totalPages > 0)
    .sort((a, b) => naturalCompare(a.title, b.title));
}

export function getLocalScores() {
  return buildScoresFromStatic();
}
