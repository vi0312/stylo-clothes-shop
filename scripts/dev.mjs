#!/usr/bin/env node
/**
 * One-command local environment for Stylo.
 *
 * Ensures the MySQL container, server dependencies, schema and seed data, then starts the
 * API. With --android it also builds the debug APK, installs it on the connected device and
 * opens the app. Safe to re-run: every step is idempotent unless --reset is passed.
 *
 * Usage: node scripts/dev.mjs [--reset] [--seed] [--android] [--no-api] [--no-docker]
 *                             [--install] [--help]
 */
import { spawn, spawnSync } from 'node:child_process'
import { copyFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SERVER_DIR = path.join(ROOT, 'server')
const ANDROID_DIR = path.join(ROOT, 'android')
const WIN = process.platform === 'win32'
const NPM = WIN ? 'npm.cmd' : 'npm'
const DEFAULT_CONTAINER = 'stylo-mysql'
const MIN_NODE = [22, 12]

const options = {
  reset: false,
  seed: false,
  android: false,
  api: true,
  docker: true,
  install: false,
  help: false,
}

for (const arg of process.argv.slice(2)) {
  switch (arg) {
    case '--reset':
      options.reset = true
      break
    case '--seed':
      options.seed = true
      break
    case '--android':
      options.android = true
      break
    case '--no-api':
      options.api = false
      break
    case '--no-docker':
      options.docker = false
      break
    case '--install':
      options.install = true
      break
    case '--help':
    case '-h':
      options.help = true
      break
    default:
      console.error(`Tùy chọn không hợp lệ: ${arg}`)
      console.error('Chạy "node scripts/dev.mjs --help" để xem hướng dẫn.')
      process.exit(2)
  }
}

if (options.help) {
  console.log(`
Stylo - dựng môi trường phát triển bằng một lệnh

  node scripts/dev.mjs [tùy chọn]

Tùy chọn:
  --reset      Xóa schema, chạy lại migration và seed (mất dữ liệu trong DB dev)
  --seed       Chạy lại seed dù DB đã có sản phẩm
  --android    Build APK debug, cài lên thiết bị/máy ảo đang kết nối rồi mở app
  --no-api     Chỉ chuẩn bị DB và dependency, không chạy server
  --no-docker  Không quản lý container MySQL; dùng DATABASE_URL trong server/.env
  --install    Chạy npm install dù node_modules đã tồn tại
  --help       In trợ giúp này
`)
  process.exit(0)
}

function step(message) {
  console.log(`\n==> ${message}`)
}

function note(message) {
  console.log(`    ${message}`)
}

function warn(message) {
  console.warn(`\nCảnh báo: ${message}`)
}

function fail(message) {
  console.error(`\nLỗi: ${message}`)
  process.exit(1)
}

function capture(command, args, options = {}) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    ...options,
  })
  return {
    status: result.status,
    stdout: result.stdout ?? '',
    stderr: result.stderr ?? '',
    error: result.error,
  }
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    stdio: 'inherit',
    cwd: ROOT,
    ...options,
  })
  if (result.error) fail(`Không chạy được "${command}". ${result.error.message}`)
  if (result.status !== 0) {
    fail(`"${[command, ...args].join(' ')}" thoát với mã ${result.status ?? 'không rõ'}.`)
  }
  return result
}

function npmInvocation(args) {
  // Node warns (DEP0190) when args are combined with shell:true, which Windows needs for
  // npm.cmd; pass a single static command string instead.
  return WIN ? { command: [NPM, ...args].join(' '), args: [] } : { command: NPM, args }
}

function npmRun(script, extra = []) {
  const args = ['run', script]
  if (extra.length > 0) args.push('--', ...extra)
  const invocation = npmInvocation(args)
  run(invocation.command, invocation.args, { cwd: SERVER_DIR, shell: WIN })
}

function readDotEnv(file) {
  if (!existsSync(file)) return {}
  const values = {}
  for (const rawLine of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = rawLine.trim()
    if (line === '' || line.startsWith('#')) continue
    const separator = line.indexOf('=')
    if (separator === -1) continue
    let value = line.slice(separator + 1).trim()
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1)
    }
    values[line.slice(0, separator).trim()] = value
  }
  return values
}

function parseDatabaseUrl(raw) {
  let url
  try {
    url = new URL(raw)
  } catch {
    fail(`DATABASE_URL không hợp lệ trong server/.env: ${raw}`)
  }
  return {
    host: url.hostname,
    port: Number(url.port || 3306),
    user: decodeURIComponent(url.username || 'stylo'),
    password: decodeURIComponent(url.password || ''),
    name: url.pathname.replace(/^\//, ''),
  }
}

function waitForPort(host, port, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  console.log(`    Đợi MySQL ở ${host}:${port} ...`)
  return new Promise((resolve) => {
    const attempt = () => {
      const socket = net.connect({ host, port })
      let settled = false
      const finish = (ready) => {
        if (settled) return
        settled = true
        socket.destroy()
        if (ready) {
          note('MySQL đã nhận kết nối.')
          resolve()
        } else if (Date.now() >= deadline) {
          fail(
            `MySQL không sẵn sàng sau ${Math.round(timeoutMs / 1000)} giây. ` +
              'Kiểm tra Docker Desktop hoặc DATABASE_URL trong server/.env.',
          )
        } else {
          setTimeout(attempt, 1000)
        }
      }
      socket.once('connect', () => finish(true))
      socket.once('error', () => finish(false))
      socket.setTimeout(2000, () => finish(false))
    }
    attempt()
  })
}

function inspectContainerEnv(container) {
  const result = capture('docker', ['inspect', '--format', '{{json .Config.Env}}', container])
  if (result.status !== 0) return {}
  try {
    const values = {}
    for (const pair of JSON.parse(result.stdout)) {
      const separator = pair.indexOf('=')
      if (separator > 0) values[pair.slice(0, separator)] = pair.slice(separator + 1)
    }
    return values
  } catch {
    return {}
  }
}

function mysqlExec(container, user, password, sql) {
  return capture('docker', [
    'exec',
    '-e',
    `MYSQL_PWD=${password}`,
    container,
    'mysql',
    `-u${user}`,
    '-N',
    '-B',
    '-e',
    sql,
  ])
}

// The MySQL entrypoint opens the TCP port before it finishes creating the app user, so a
// port check alone can race the GRANT below.
function waitForMysqlLogin(container, user, password, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  return new Promise((resolve) => {
    const attempt = () => {
      if (mysqlExec(container, user, password, 'SELECT 1').status === 0) return resolve(true)
      if (Date.now() >= deadline) return resolve(false)
      setTimeout(attempt, 1000)
    }
    attempt()
  })
}

function ensureManagedDatabase(env) {
  if (!options.docker) {
    note('Bỏ qua Docker (--no-docker): dùng DATABASE_URL trong server/.env.')
    return null
  }

  const db = parseDatabaseUrl(env.DATABASE_URL)
  const localHosts = new Set(['127.0.0.1', 'localhost', '::1'])
  if (!localHosts.has(db.host) || db.port !== 3306 || db.name !== 'stylo') {
    note('DATABASE_URL không trỏ vào MySQL cục bộ mặc định; bỏ qua Docker.')
    return null
  }

  if (capture('docker', ['--version']).status !== 0) {
    fail(
      'Không tìm thấy Docker. Cài Docker Desktop/Engine, hoặc trỏ DATABASE_URL vào MySQL ' +
        'sẵn có và chạy lại với --no-docker.',
    )
  }
  if (capture('docker', ['info']).status !== 0) {
    fail('Docker chưa chạy. Mở Docker Desktop (đợi engine sẵn sàng) rồi chạy lại lệnh này.')
  }

  const container = DEFAULT_CONTAINER
  const exists = capture('docker', ['container', 'inspect', container]).status === 0
  if (!exists) {
    step(`Tạo container MySQL 8.4 "${container}"`)
    run('docker', [
      'run',
      '-d',
      '--name',
      container,
      '-e',
      'MYSQL_ROOT_PASSWORD=stylo-root',
      '-e',
      'MYSQL_DATABASE=stylo',
      '-e',
      'MYSQL_USER=stylo',
      '-e',
      'MYSQL_PASSWORD=stylo',
      '-p',
      '3306:3306',
      'mysql:8.4',
    ])
  } else {
    const running = capture('docker', ['inspect', '--format', '{{.State.Running}}', container])
    if (running.stdout.trim() !== 'true') {
      step(`Khởi động container "${container}"`)
      run('docker', ['start', container])
    } else {
      note(`Container "${container}" đang chạy.`)
    }
  }

  return { container, db }
}

async function ensureTestSchema(managed) {
  if (!managed) return
  const { container, db } = managed
  const testEnv = readDotEnv(path.join(SERVER_DIR, '.env.test'))
  const testDb = testEnv.DATABASE_URL ? parseDatabaseUrl(testEnv.DATABASE_URL).name : 'stylo_test'
  if (!/^[A-Za-z0-9_]+$/.test(testDb)) return

  const containerEnv = inspectContainerEnv(container)
  const rootPassword = containerEnv.MYSQL_ROOT_PASSWORD ?? 'stylo-root'
  const appUser = containerEnv.MYSQL_USER ?? db.user
  const appPassword = containerEnv.MYSQL_PASSWORD ?? db.password

  step(`Chuẩn bị schema test "${testDb}"`)
  if (!(await waitForMysqlLogin(container, 'root', rootPassword, 90_000))) {
    fail(
      `Không đăng nhập được MySQL root trong container "${container}". ` +
        `Xóa container và chạy lại, hoặc tạo schema "${testDb}" thủ công.`,
    )
  }
  if (!(await waitForMysqlLogin(container, appUser, appPassword, 90_000))) {
    fail(`Tài khoản MySQL "${appUser}" chưa sẵn sàng trong container "${container}".`)
  }

  const grant = `CREATE DATABASE IF NOT EXISTS \`${testDb}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
       GRANT ALL PRIVILEGES ON \`${testDb}\`.* TO '${appUser}'@'%';
       GRANT ALL PRIVILEGES ON *.* TO '${appUser}'@'%';`
  let result
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    result = mysqlExec(container, 'root', rootPassword, grant)
    if (result.status === 0) break
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  if (result.status !== 0) {
    fail(`Không tạo được schema test: ${result.stderr.trim() || 'mysql thoát với lỗi không rõ.'}`)
  }
  note('Đã tạo schema test và cấp quyền shadow database cho migrate dev.')
}

function ensureDependencies() {
  const nodeModules = path.join(SERVER_DIR, 'node_modules')
  const stamp = path.join(nodeModules, '.package-lock.json')
  if (options.install || !existsSync(stamp)) {
    step('Cài dependency server (npm install)')
    npmRun('install')
  } else {
    note('server/node_modules đã có; bỏ qua npm install (dùng --install để chạy lại).')
  }

  if (!existsSync(path.join(SERVER_DIR, 'src', 'generated', 'prisma'))) {
    step('Sinh Prisma client')
    npmRun('db:generate')
  }
}

async function countProducts(db) {
  const require = createRequire(path.join(SERVER_DIR, 'package.json'))
  let mariadb
  try {
    mariadb = require('mariadb')
  } catch {
    return -1
  }
  let connection
  try {
    connection = await mariadb.createConnection({
      host: db.host,
      port: db.port,
      user: db.user,
      password: db.password,
      database: db.name,
      connectTimeout: 5000,
    })
    const rows = await connection.query('SELECT COUNT(*) AS n FROM product')
    return Number(rows[0]?.n ?? 0)
  } catch (error) {
    warn(`Không đọc được số sản phẩm (${error.message}). Sẽ chạy seed để đảm bảo dữ liệu.`)
    return -1
  } finally {
    if (connection) await connection.end().catch(() => {})
  }
}

async function ensureSchema(db) {
  if (options.reset) {
    step('Reset schema, migration và seed (--reset)')
    npmRun('db:reset')
    return
  }

  step('Áp dụng migration (prisma migrate deploy)')
  npmRun('db:deploy')

  const count = await countProducts(db)
  if (options.seed || count === 0 || count === -1) {
    step('Seed dữ liệu mẫu')
    npmRun('db:seed')
  } else {
    note(`DB đã có ${count} sản phẩm; bỏ qua seed (dùng --seed để chạy lại).`)
  }
}

function readJavaProperty(file, key) {
  if (!existsSync(file)) return null
  const prefix = new RegExp(`^\\s*${key}\\s*=\\s*(.+)$`, 'm')
  const match = readFileSync(file, 'utf8').match(prefix)
  if (!match) return null
  return match[1].trim().replace(/\\:/g, ':').replace(/\\\\/g, '\\')
}

function findAndroidSdk() {
  const fromLocalProperties = readJavaProperty(path.join(ANDROID_DIR, 'local.properties'), 'sdk.dir')
  if (fromLocalProperties && existsSync(fromLocalProperties)) return fromLocalProperties

  for (const candidate of [process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT]) {
    if (candidate && existsSync(candidate)) return candidate
  }

  const home = process.env.USERPROFILE ?? process.env.HOME
  const candidates = WIN
    ? [path.join(home ?? '', 'AppData', 'Local', 'Android', 'Sdk')]
    : process.platform === 'darwin'
      ? [path.join(home ?? '', 'Library', 'Android', 'sdk')]
      : [path.join(home ?? '', 'Android', 'Sdk')]
  return candidates.find((candidate) => existsSync(candidate)) ?? null
}

function ensureLocalProperties(sdk) {
  const file = path.join(ANDROID_DIR, 'local.properties')
  if (existsSync(file)) return
  writeFileSync(file, `sdk.dir=${sdk.replace(/\\/g, '/')}\n`)
  note(`Đã tạo android/local.properties trỏ tới ${sdk}.`)
}

function findAdb(sdk) {
  if (sdk) {
    const fromSdk = path.join(sdk, 'platform-tools', WIN ? 'adb.exe' : 'adb')
    if (existsSync(fromSdk)) return fromSdk
  }
  if (capture(WIN ? 'where' : 'which', ['adb']).status === 0) return 'adb'
  return null
}

function listDevices(adb) {
  const result = capture(adb, ['devices'])
  if (result.status !== 0) return []
  return result.stdout
    .split(/\r?\n/)
    .slice(1)
    .map((line) => line.trim().split(/\s+/))
    .filter((parts) => parts[1] === 'device')
    .map((parts) => parts[0])
}

function prepareAndroid(port) {
  step('Chuẩn bị Android (--android)')
  const sdk = findAndroidSdk()
  if (!sdk) {
    warn(
      'Không tìm thấy Android SDK. Cài Android Studio, đặt ANDROID_HOME, hoặc tạo ' +
        'android/local.properties với sdk.dir=... rồi chạy lại.',
    )
    return null
  }
  ensureLocalProperties(sdk)

  const adb = findAdb(sdk)
  if (!adb) {
    warn('Không tìm thấy adb (platform-tools). Bỏ qua bước cài app.')
    return null
  }

  const devices = listDevices(adb)
  if (devices.length === 0) {
    warn(
      'Không có thiết bị/máy ảo Android nào đang kết nối. Mở AVD trong Android Studio ' +
        '(hoặc cắm máy thật bật USB debugging) rồi chạy lại với --android.',
    )
    return null
  }

  const serial = devices[0]
  if (devices.length > 1) {
    note(`Có ${devices.length} thiết bị; dùng ${serial}.`)
  }

  note(`adb reverse tcp:${port} tcp:${port} trên ${serial}`)
  run(adb, ['-s', serial, 'reverse', `tcp:${port}`, `tcp:${port}`])

  step(`Build APK debug (${path.relative(ROOT, ANDROID_DIR)})`)
  if (WIN) {
    run('gradlew.bat :app:assembleDebug', [], { cwd: ANDROID_DIR, shell: true })
  } else {
    run('./gradlew', [':app:assembleDebug'], { cwd: ANDROID_DIR })
  }

  const apk = path.join(ANDROID_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
  if (!existsSync(apk)) fail(`Không thấy APK tại ${apk}.`)
  step('Cài APK lên thiết bị')
  run(adb, ['-s', serial, 'install', '-r', apk])
  return { adb, serial }
}

async function waitForApi(port, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/health`, {
        signal: AbortSignal.timeout(1500),
      })
      if (response.ok) return true
    } catch {
      // Server is still starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  return false
}

function printSummary(env, port, android) {
  const adminEmail = env.SEED_ADMIN_EMAIL ?? 'admin@stylo.local'
  const adminPassword = env.SEED_ADMIN_PASSWORD ?? 'Admin@12345'
  console.log(`
Stylo đã sẵn sàng
  API        http://127.0.0.1:${port}/api/v1
  Health     http://127.0.0.1:${port}/health
  Admin      ${adminEmail} / ${adminPassword}
  Khách      customer01@stylo.local / Customer@12345
  Seed khác  customer02..11@stylo.local / Customer@12345 (customer12 bị khóa)

${android ? `  App        đã cài trên ${android.serial}` : options.android ? '  App        chưa cài: không có thiết bị/máy ảo kết nối' : '  App        thêm --android để build, cài và mở app'}

  Ctrl+C để dừng API.
`)
}

function launchAndroid(android) {
  step('Mở app trên thiết bị')
  run(android.adb, [
    '-s',
    android.serial,
    'shell',
    'am',
    'start',
    '-n',
    'com.stylo.shop/.MainActivity',
  ])
}

async function main() {
  const [major, minor] = process.versions.node.split('.').map(Number)
  if (major < MIN_NODE[0] || (major === MIN_NODE[0] && minor < MIN_NODE[1])) {
    fail(
      `Cần Node.js >= ${MIN_NODE[0]}.${MIN_NODE[1]} (đang dùng ${process.versions.node}). ` +
        'Cài Node 24 LTS rồi chạy lại.',
    )
  }

  step('Chuẩn bị server/.env')
  const envPath = path.join(SERVER_DIR, '.env')
  if (!existsSync(envPath)) {
    copyFileSync(path.join(SERVER_DIR, '.env.example'), envPath)
    note('Đã tạo server/.env từ server/.env.example (PAYMENT_PROVIDER=MOCK).')
  } else {
    note('server/.env đã tồn tại; giữ nguyên.')
  }
  const env = readDotEnv(envPath)
  if (!env.DATABASE_URL) fail('server/.env thiếu DATABASE_URL.')
  if (env.PAYMENT_PROVIDER && env.PAYMENT_PROVIDER !== 'MOCK') {
    note(`PAYMENT_PROVIDER=${env.PAYMENT_PROVIDER} (demo mặc định dùng MOCK).`)
  }

  const db = parseDatabaseUrl(env.DATABASE_URL)
  const managed = ensureManagedDatabase(env)
  if (managed) {
    await waitForPort(managed.db.host, managed.db.port, 120_000)
  } else if (options.docker && ['127.0.0.1', 'localhost', '::1'].includes(db.host)) {
    await waitForPort(db.host, db.port, 15_000)
  }
  await ensureTestSchema(managed)

  ensureDependencies()
  await ensureSchema(db)

  const port = Number(env.PORT || 8787)
  let android = null
  if (options.android) android = prepareAndroid(port)

  if (!options.api) {
    console.log('\nĐã chuẩn bị xong DB và dependency (--no-api).')
    return
  }

  if (await waitForApi(port, 1500)) {
    note(`API đã chạy sẵn tại cổng ${port}; không khởi động thêm.`)
    if (android) launchAndroid(android)
    printSummary(env, port, android)
    return
  }

  step(`Khởi động API (http://127.0.0.1:${port})`)
  const dev = npmInvocation(['run', 'dev'])
  const child = spawn(dev.command, dev.args, {
    cwd: SERVER_DIR,
    stdio: 'inherit',
    shell: WIN,
    env: process.env,
  })
  child.on('error', (error) => fail(`Không khởi động được API: ${error.message}`))
  child.on('exit', (code, signal) => {
    process.exitCode = code ?? (signal ? 1 : 0)
  })

  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => {
      if (!child.killed) child.kill(signal)
    })
  }

  if (!(await waitForApi(port, 60_000))) {
    fail('API không phản hồi /health sau 60 giây. Xem log phía trên để biết nguyên nhân.')
  }

  if (android) launchAndroid(android)

  printSummary(env, port, android)
}

await main()
