const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

function requireTs(relativePath) {
  const absolutePath = path.join(__dirname, '..', relativePath);
  const source = fs.readFileSync(absolutePath, 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      strict: true,
    },
  });
  const module = { exports: {} };
  const fn = new Function('require', 'module', 'exports', outputText);
  fn(require, module, module.exports);
  return module.exports;
}

function unsignedJwt(claims) {
  const encode = (value) =>
    Buffer.from(JSON.stringify(value))
      .toString('base64url');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode(claims)}.`;
}

const {
  isJwtExpired,
  isLogoutStatusAccepted,
  shouldRestoreStoredSession,
} = requireTs('src/utils/authSession.ts');

const expiredToken = unsignedJwt({ sub: 'user_123', iat: 1_000, exp: 1_100 });
assert.equal(isJwtExpired(expiredToken), true);
assert.equal(shouldRestoreStoredSession(expiredToken, '{"nickname":"천재승"}'), false);

const futureExp = Math.floor(Date.now() / 1000) + 3_600;
const validToken = unsignedJwt({ sub: 'user_123', iat: futureExp - 60, exp: futureExp });
assert.equal(isJwtExpired(validToken), false);
assert.equal(shouldRestoreStoredSession(validToken, '{"nickname":"천재승"}'), true);

assert.equal(shouldRestoreStoredSession(null, '{"nickname":"천재승"}'), false);
assert.equal(shouldRestoreStoredSession(validToken, null), false);

assert.equal(isLogoutStatusAccepted(204), true);
assert.equal(isLogoutStatusAccepted(401), true);
assert.equal(isLogoutStatusAccepted(500), false);
