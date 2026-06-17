#!/usr/bin/env node
// 하네스 훅 디스패처 (의존성 0). Claude Code 가 stdin 으로 넘기는 JSON 을 읽어 DDD 위반을 검사한다.
// block=exit 2(에이전트가 stderr 보고 즉시 수정) / warn=exit 0(비차단 권고).
//
//   guard      (PostToolUse Edit|Write|MultiEdit) — DDD 22원칙 중 정적 검증 가능분 [핵심]
//   protect    (PreToolUse  Edit|Write|MultiEdit) — 보호 경로(마이그레이션) 차단
//   bash       (PreToolUse  Bash)                 — 금지 명령 차단(mvn/전역 gradle)
//   checklist  (Stop)                             — 완료 전 자가 체크리스트
//
// 설계: 절대 에이전트 루프를 깨지 않는다(예외는 삼키고 exit 0). 정규식 + import-follow(참조 타입 파일만
// 읽음, 전체 repo 스캔 X). 휴리스틱도 기본 block(config의 checks에서 warn/off로 조정). 정밀 강제는 ArchUnit(CI)이 보완.

import { readFileSync, existsSync } from "node:fs";
import { extname, basename, join } from "node:path";

const CONFIG_URL = new URL("./harness.config.json", import.meta.url);
const loadConfig = () => JSON.parse(readFileSync(CONFIG_URL, "utf8"));
const readStdin = () => { try { return JSON.parse(readFileSync(0, "utf8") || "{}"); } catch { return {}; } };

// 미니 glob → RegExp. `**`=임의 경로, `*`=슬래시 제외.
function globToRegex(glob) {
  let re = "";
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === "*") {
      if (glob[i + 1] === "*") { re += ".*"; i++; if (glob[i + 1] === "/") i++; }
      else re += "[^/]*";
    } else if ("\\^$+?.()|{}[]".includes(c)) re += "\\" + c;
    else re += c;
  }
  return new RegExp("^" + re + "$");
}
const matchAny = (p, globs = []) => globs.some((g) => globToRegex(g).test(p));
function layerOf(filePath, layers = {}) {
  for (const [name, globs] of Object.entries(layers)) if (matchAny(filePath, globs)) return name;
  return null;
}
function extractImports(content, patterns) {
  const found = [];
  for (const pat of patterns) {
    const re = new RegExp(pat, "g");
    let m;
    while ((m = re.exec(content)) !== null) { const t = m.slice(1).find(Boolean); if (t) found.push(t); }
  }
  return found;
}
const blockPre = (reason) => { process.stderr.write(reason + "\n"); process.exit(2); };

// ── import-follow: 참조 타입의 소스 파일만 찾아 어노테이션을 본다(전체 스캔 X) ──
function sourceRootOf(filePath, markers = []) {
  const p = filePath.replace(/\\/g, "/");
  for (const m of markers) { const i = p.lastIndexOf("/" + m + "/"); if (i >= 0) return p.slice(0, i + 1 + m.length); }
  return null;
}
function readType(fqcn, root) {
  if (!root) return null;
  for (const ext of [".java", ".kt"]) {
    const f = join(root, fqcn.replace(/\./g, "/") + ext);
    if (existsSync(f)) { try { return readFileSync(f, "utf8"); } catch { return null; } }
  }
  return null;
}
const hasAnno = (c, name) => new RegExp("@" + name + "\\b").test(c);
const simpleName = (fqcn) => fqcn.slice(fqcn.lastIndexOf(".") + 1);

// ── 자기 파일 분석 헬퍼(휴리스틱) ──
const isRecord = (c) => /\brecord\s+\w+/.test(c);
const hasSetterOrMutableLombok = (c) =>
  /\bpublic\s+[\w<>\[\].]+\s+set[A-Z]\w*\s*\(/.test(c) || /@Setter\b/.test(c) || /@Data\b/.test(c);
function hasNonFinalInstanceField(c) {
  const re = /\n[ \t]*(private|protected|public)\b([^;=\n(){}]*?)\s+\w+\s*[;=]/g;
  let m;
  while ((m = re.exec(c)) !== null) if (!/\bfinal\b|\bstatic\b/.test(m[0])) return true;
  return false;
}
function businessMethodCount(c) {
  const re = /\bpublic\s+(?!class\b|interface\b|enum\b|record\b)[\w<>\[\].]+\s+(\w+)\s*\(/g;
  let m, n = 0;
  while ((m = re.exec(c)) !== null) {
    const name = m[1];
    if (!/^(get|set|is)[A-Z]/.test(name) && !["equals", "hashCode", "toString", "builder"].includes(name)) n++;
  }
  return n;
}
const fieldCount = (c) => (c.match(/\n[ \t]*(private|protected)\s+[\w<>\[\].]+\s+\w+\s*[;=]/g) || []).length;

// ── guard ──
function guard(input, cfg) {
  const filePath = input?.tool_input?.file_path;
  if (!filePath || !cfg.sourceExtensions.includes(extname(filePath))) process.exit(0);
  if (!existsSync(filePath)) process.exit(0);

  const content = readFileSync(filePath, "utf8");
  const layer = layerOf(filePath, cfg.layers);
  const base = basename(filePath, extname(filePath));
  const mk = cfg.markers || {};
  const checks = cfg.checks || {};
  const sev = (k) => checks[k] || "off";
  const block = [], warn = [];
  const add = (s, msg) => (s === "block" ? block : warn).push((s === "block" ? "  [차단] " : "  [경고] ") + msg);
  const self = (n) => hasAnno(content, n);

  // (기존 block) 콘텐츠 패턴(필드주입, 도메인 @Setter/@Data/public setter)
  for (const { pattern, message, layers } of cfg.forbiddenContentPatterns || []) {
    if (layers && !layers.includes(layer)) continue;
    if (new RegExp(pattern, "m").test(content)) add("block", message);
  }
  // (기존 block) 레이어 금지 import
  const imports = extractImports(content, cfg.importPatterns);
  const forbidden = Array.isArray(cfg.forbiddenImports?.[layer]) ? cfg.forbiddenImports[layer] : null;
  if (forbidden) for (const imp of imports) { const hit = forbidden.find((t) => imp.includes(t)); if (hit) add("block", `import "${imp}" → '${layer}' 금지 토큰 "${hit}"`); }
  // (기존 block) 금지 파일명 — 도메인 *RepositoryImpl
  const badNames = Array.isArray(cfg.forbiddenFilenames?.[layer]) ? cfg.forbiddenFilenames[layer] : null;
  if (badNames) { const h = badNames.find((re) => new RegExp(re).test(base)); if (h) add("block", `파일명 "${base}" → '${layer}' 금지 "${h}" (DIP: 구현은 infrastructure)`); }

  // (신규) VO/도메인이벤트 불변성 (#16/#20)
  if (sev("vodImmutability") !== "off" && (self(mk.valueObject || "ValueObject") || self(mk.domainEvent || "DomainEvent")))
    if (!isRecord(content) && (hasSetterOrMutableLombok(content) || hasNonFinalInstanceField(content)))
      add(sev("vodImmutability"), "불변성 위반: @ValueObject/@DomainEvent 는 불변이어야 함(setter·비-final 필드 금지, record 권장).");

  // (신규) DIP: 도메인 *Repository 는 인터페이스 (#4)
  if (sev("repositoryInterface") !== "off" && layer === "domain" && /Repository$/.test(base) && !/RepositoryImpl$/.test(base))
    if (!/\binterface\s+\w*Repository\b/.test(content))
      add(sev("repositoryInterface"), `DIP 위반: 도메인 '${base}' 는 인터페이스여야 함(구현은 infrastructure).`);

  // (신규) 빈약 모델 (#11)
  if (sev("anemicModel") !== "off" && layer === "domain" && (self(mk.aggregateRoot || "AggregateRoot") || /@Entity\b/.test(content)))
    if (fieldCount(content) > 0 && businessMethodCount(content) === 0)
      add(sev("anemicModel"), "빈약 모델 의심: 행위 메서드 없는 데이터 홀더. 비즈니스 행위를 엔티티에 캡슐화하세요.");

  // (신규) 최소 애그리거트 (#10)
  if (sev("minAggregate") !== "off" && self(mk.aggregateRoot || "AggregateRoot")) {
    const max = cfg.thresholds?.maxAggregateFields ?? 12, n = fieldCount(content);
    if (n > max) add(sev("minAggregate"), `애그리거트 과대: 필드 ${n} > ${max}. VO 묶음/독립 애그리거트 분리 검토.`);
  }
  // (신규) 도메인 서비스 무상태 (#22)
  if (sev("domainServiceStateless") !== "off" && (self(mk.domainService || "DomainService") || /DomainService$/.test(base)))
    if (hasNonFinalInstanceField(content)) add(sev("domainServiceStateless"), "도메인 서비스 무상태 위반: 가변 인스턴스 필드 보유. 무상태로 유지하세요.");

  // (신규) 도메인 팩토리 (#15)
  if (sev("domainFactory") !== "off" && self(mk.aggregateRoot || "AggregateRoot")) {
    const m = content.match(new RegExp("public\\s+" + base + "\\s*\\(([^)]*)\\)"));
    const args = m && m[1].trim() ? m[1].split(",").length : 0;
    const hasFactory = /\bpublic\s+static\s+[\w<>\[\].]+\s+\w+\s*\(/.test(content);
    if (args >= 3 && !hasFactory) add(sev("domainFactory"), `팩토리 권장: public 다인자 생성자(${args}개) — 정적 팩토리 메서드로 생성 무결성 보장.`);
  }
  // (신규) 도메인 이벤트 과거형 명명 (#20)
  if (sev("pastTenseEvent") !== "off" && self(mk.domainEvent || "DomainEvent"))
    if (!/(ed|en|t)$/.test(base)) add(sev("pastTenseEvent"), `도메인 이벤트 명명: '${base}' 는 과거형 권장(예: OrderPlaced).`);

  // (신규 import-follow) 애그리거트 경계(#9) / ID 참조(#13) — 도메인 한정
  if (layer === "domain" && (sev("aggregateBoundary") !== "off" || sev("idReference") !== "off")) {
    const root = sourceRootOf(filePath, cfg.sourceRootMarkers);
    for (const imp of imports) {
      const tc = readType(imp, root);
      if (!tc) continue;
      const sn = simpleName(imp);
      if (sev("aggregateBoundary") !== "off" && hasAnno(tc, mk.aggregateInternal || "AggregateInternal"))
        add(sev("aggregateBoundary"), `애그리거트 경계 위반: 다른 애그리거트 내부 타입 '${sn}' 직접 참조. AR 통해 접근하세요.`);
      if (sev("idReference") !== "off" && hasAnno(tc, mk.aggregateRoot || "AggregateRoot")
        && new RegExp("(private|protected|public)\\s+" + sn + "\\b").test(content))
        add(sev("idReference"), `ID 참조 권장: 다른 애그리거트 루트 '${sn}' 를 객체로 직접 참조. 식별자(ID)로 참조하세요.`);
    }
  }

  if (block.length === 0 && warn.length === 0) process.exit(0);
  process.stderr.write(
    `${block.length ? "❌ DDD 위반(차단)" : "⚠️ DDD 경고(권고)"}: ${filePath}\n` +
    [...block, ...warn].join("\n") +
    `\n\n규칙/세버리티 조정: .claude/hooks/harness.config.json (checks). 구조 규칙의 정밀 강제는 ArchUnit(CI).\n`
  );
  process.exit(block.length ? 2 : 0);
}

function protect(input, cfg) {
  const filePath = input?.tool_input?.file_path;
  if (filePath && matchAny(filePath, cfg.protectedPaths))
    blockPre(`❌ 보호 경로 수정 금지: ${filePath}\n기존 DB 마이그레이션 파일은 수정/삭제하지 않습니다. 새 마이그레이션을 추가하세요.`);
  process.exit(0);
}
function bashGuard(input, cfg) {
  const command = input?.tool_input?.command || "";
  if ((cfg.forbiddenCommands || []).some((re) => new RegExp(re).test(command)))
    blockPre(`❌ 명령 규칙: ${cfg.commandRule || "허용되지 않은 명령입니다."}\n명령: ${command}`);
  process.exit(0);
}
function checklist(input) {
  if (input?.stop_hook_active) process.exit(0);
  process.stderr.write(
    `⛔ 완료 전 자가 체크리스트(pre-completion):\n` +
    `  1) 최초 요구사항/이슈와 결과물을 대조해 누락이 없는가?\n` +
    `  2) 변경 후 빌드/테스트를 실제로 실행해 통과를 확인했는가? (추측 금지)\n` +
    `  3) 도메인 순수성·애그리거트 경계 등 DDD 원칙을 지켰는가?\n` +
    `위 항목을 점검·보고한 뒤 종료하세요.\n`
  );
  process.exit(2);
}

const cmd = process.argv[2];
try {
  const input = readStdin();
  const cfg = loadConfig();
  if (cmd === "guard") guard(input, cfg);
  else if (cmd === "protect") protect(input, cfg);
  else if (cmd === "bash") bashGuard(input, cfg);
  else if (cmd === "checklist") checklist(input);
  else process.exit(0);
} catch {
  process.exit(0); // 훅이 깨져도 에이전트 작업을 막지 않는다.
}
