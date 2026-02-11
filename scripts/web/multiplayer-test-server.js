const http = require('http');
const { WebSocketServer } = require('ws');
const { URL } = require('url');

const port = Number(process.env.WEB_MP_SERVER_PORT || '19090');
const host = process.env.WEB_MP_SERVER_HOST || '127.0.0.1';

const files = new Map();
const authPasswords = new Map();

function withCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,PUT,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Authorization,Content-Type');
}

function parseBasicAuth(header) {
  if (!header || typeof header !== 'string') return null;
  const match = header.match(/^\s*Basic\s+(.+)$/i);
  if (!match) return null;
  try {
    const decoded = Buffer.from(match[1], 'base64').toString('utf8');
    const index = decoded.indexOf(':');
    if (index < 0) return null;
    return { user: decoded.slice(0, index), pass: decoded.slice(index + 1) };
  } catch (_) {
    return null;
  }
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.setEncoding('utf8');
    req.on('data', chunk => { data += chunk; });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  withCors(res);
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    return res.end();
  }

  const url = new URL(req.url || '/', `http://${req.headers.host || host}`);

  if (url.pathname === '/isalive') {
    res.setHeader('Content-Type', 'application/json');
    res.statusCode = 200;
    return res.end(JSON.stringify({ authVersion: 1, chatVersion: 1 }));
  }

  if (url.pathname === '/auth') {
    const auth = parseBasicAuth(req.headers.authorization);
    if (!auth) {
      res.statusCode = 400;
      return res.end('Missing auth header');
    }
    if (req.method === 'GET') {
      if (!authPasswords.has(auth.user)) {
        res.statusCode = 204;
        return res.end();
      }
      if (authPasswords.get(auth.user) === auth.pass) {
        res.statusCode = 200;
        return res.end();
      }
      res.statusCode = 401;
      return res.end('Unauthorized');
    }
    if (req.method === 'PUT') {
      const body = await readBody(req);
      if (!body || body.length < 6) {
        res.statusCode = 400;
        return res.end('Password should be at least 6 characters long');
      }
      const existing = authPasswords.get(auth.user);
      if (existing && existing !== auth.pass) {
        res.statusCode = 401;
        return res.end('Unauthorized');
      }
      authPasswords.set(auth.user, body);
      res.statusCode = 200;
      return res.end();
    }
  }

  if (url.pathname.startsWith('/files/')) {
    const fileName = decodeURIComponent(url.pathname.substring('/files/'.length));
    const auth = parseBasicAuth(req.headers.authorization);
    if (!auth) {
      res.statusCode = 401;
      return res.end('Unauthorized');
    }
    if (req.method === 'PUT') {
      const body = await readBody(req);
      files.set(fileName, body);
      res.statusCode = 200;
      return res.end();
    }
    if (req.method === 'GET') {
      if (!files.has(fileName)) {
        res.statusCode = 404;
        return res.end('File does not exist');
      }
      res.statusCode = 200;
      return res.end(files.get(fileName));
    }
  }

  res.statusCode = 404;
  res.end('Not found');
});

const wss = new WebSocketServer({ noServer: true });
const subscriptions = new Map();

function broadcast(gameId, payload) {
  const clients = subscriptions.get(gameId);
  if (!clients) return;
  const message = JSON.stringify(payload);
  for (const ws of clients) {
    if (ws.readyState === ws.OPEN) ws.send(message);
  }
}

wss.on('connection', (ws) => {
  ws.on('message', (data) => {
    let payload = null;
    try {
      payload = JSON.parse(String(data));
    } catch (_) {
      return;
    }
    if (!payload || typeof payload.type !== 'string') return;
    if (payload.type === 'join' && Array.isArray(payload.gameIds)) {
      payload.gameIds.forEach((gameId) => {
        if (!subscriptions.has(gameId)) subscriptions.set(gameId, new Set());
        subscriptions.get(gameId).add(ws);
      });
      ws.send(JSON.stringify({ type: 'joinSuccess', gameIds: payload.gameIds }));
    }
    if (payload.type === 'leave' && Array.isArray(payload.gameIds)) {
      payload.gameIds.forEach((gameId) => {
        const set = subscriptions.get(gameId);
        if (set) set.delete(ws);
      });
    }
    if (payload.type === 'chat') {
      const gameId = payload.gameId;
      if (!gameId || !subscriptions.has(gameId)) {
        return ws.send(JSON.stringify({ type: 'error', message: 'You are not subscribed to this channel!' }));
      }
      broadcast(gameId, {
        type: 'chat',
        civName: payload.civName || 'Unknown',
        message: payload.message || '',
        gameId,
      });
    }
  });

  ws.on('close', () => {
    for (const set of subscriptions.values()) {
      set.delete(ws);
    }
  });
});

server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || host}`);
  if (url.pathname !== '/chat') {
    socket.destroy();
    return;
  }
  const authHeader = req.headers.authorization || url.searchParams.get('auth');
  const auth = parseBasicAuth(authHeader);
  if (!auth) {
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => {
    wss.emit('connection', ws, req);
  });
});

server.listen(port, host, () => {
  process.stdout.write(`multiplayer-test-server listening on http://${host}:${port}\n`);
});
