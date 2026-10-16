/*
 * Regenerates android/app/src/main/assets/briefing/page.html from the Mac widget's
 * briefing renderer (ai_news_mac_widget/backend/apps/api/src/briefing.ts), so both apps
 * show the same page. Everything that depends on the briefing or its language becomes a
 * {{PLACEHOLDER}} that BriefingEngine.renderHtml fills in.
 *
 * Run from this repo's root, with the Mac repo next to it:
 *   npx --prefix ../ai_news_mac_widget tsx scripts/sync-briefing-page.mts
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const macBriefing = path.resolve(root, '../ai_news_mac_widget/backend/apps/api/src/briefing.ts');
const { renderBriefingHtml } = await import(macBriefing);

const TITLE = 'TITLE_SENTINEL';
let html: string = renderBriefingHtml(TITLE, [], 12, 0, 0, true, [], 'en');

function swap(pattern: RegExp, replacement: string) {
  if (!pattern.test(html)) throw new Error(`Not found in the Mac page: ${pattern}`);
  html = html.replace(pattern, replacement);
}

swap(/<html lang="en">/, '<html lang="{{LANG}}">');
swap(new RegExp(`<title>${TITLE}</title>`), '<title>{{TITLE}}</title>');
swap(new RegExp(`<h1>${TITLE}</h1>\\n    <p>[\\s\\S]*?</p>`), '<h1>{{TITLE}}</h1>\n    <p>{{COUNT_LINE}}</p>');
swap(/<footer>\n    <p>[\s\S]*?<\/p>\n    <ul>[\s\S]*?<\/ul>/, '<footer>\n    <p>{{FOOTER}}</p>\n    <ul>{{LEGEND}}</ul>');
swap(/aria-label="Importance of each criterion"/, 'aria-label="{{IMPORTANCE_ARIA}}"');
swap(/<span class="eq-title">Importance<\/span>/, '<span class="eq-title">{{IMPORTANCE}}</span>');
swap(/aria-label="Close"/, 'aria-label="{{CLOSE}}"');
swap(/<p class="eq-hint">[^<]*<\/p>/, '<p class="eq-hint">{{IMPORTANCE_HINT}}</p>');
swap(/aria-expanded="false">Importance · /, 'aria-expanded="false">{{IMPORTANCE}} · ');
swap(/aria-label="Go to top"/, 'aria-label="{{GO_TOP}}"');
swap(/aria-label="Go to bottom"/, 'aria-label="{{GO_BOTTOM}}"');
swap(/(<script id="briefing-data" type="application\/json">)[\s\S]*?(<\/script>)/, '$1{{DATA_JSON}}$2');

const out = path.join(root, 'android/app/src/main/assets/briefing/page.html');
fs.writeFileSync(out, html);
console.log(`Wrote ${path.relative(root, out)} (${html.length} bytes)`);
