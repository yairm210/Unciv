function parseArgs(raw) {
  return String(raw || '').trim().split(/\s+/).filter(Boolean);
}

function defaultChromiumArgs() {
  if (process.platform === 'darwin') {
    return [
      '--use-angle=swiftshader',
      '--enable-unsafe-swiftshader',
      '--ignore-gpu-blocklist',
      '--disable-background-timer-throttling',
      '--disable-backgrounding-occluded-windows',
      '--disable-renderer-backgrounding',
    ];
  }
  return [
    '--use-gl=swiftshader',
    '--disable-background-timer-throttling',
    '--disable-backgrounding-occluded-windows',
    '--disable-renderer-backgrounding',
  ];
}

function resolveChromiumArgs(raw) {
  const parsed = parseArgs(raw);
  return parsed.length > 0 ? parsed : defaultChromiumArgs();
}

module.exports = {
  resolveChromiumArgs,
};
