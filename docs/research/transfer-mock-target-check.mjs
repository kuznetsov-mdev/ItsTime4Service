import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
for (const match of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(match[1]);
const start = html.indexOf('  var transferState = null;');
const end = html.indexOf('  function paintInteractive()', start);
assert.ok(start >= 0 && end > start);
const cars = [{ id: 'car1', brand: 'Test car', mileageKm: 90000, history: [], tasks: [
    { id: 'local1', name: 'Oil', intervalKm: 10000, lastKm: 80000, lastDate: '2026-10-01' },
    { id: 'local2', name: 'Oil', intervalKm: 20000, lastKm: 70000, lastDate: '2026-09-01' },
] }];
const context = vm.createContext({
    cars, soldCars: [], istate: { detailId: 'car1' },
    carById: id => cars.find(car => car.id === id), fmtKm: value => `${value} км`,
    icon: () => '', paintInteractive: () => {},
});
vm.runInContext(html.slice(start, end), context);
context.transferDemoFile = { name: 'Test.it4s', kind: 'car', groups: {}, sold: [], cars: [{ brand: 'Test', tasks: [
    { id: 'incoming1', name: 'Oil', intervalKm: 15000 },
] }] };
context.transferStart('conflicts');
let checks = 0;
function check(value, message) { assert.ok(value, message); checks++; }
const state = context.transferState;
const incoming = state.incoming.cars[0].tasks[0];
check(context.transferTaskChoicesReady(), 'skip remains the default');
check(Object.keys(state.taskTargets).length === 0, 'no default mapping');
state.decisions.incoming1 = 'update';
check(context.transferChosenTask(incoming) === null, 'no implicit choice among duplicate names');
check(!context.transferTaskChoicesReady(), 'update waits for a concrete task');
const options = context.transferTaskTargetOptions(incoming);
check((options.match(/data-transfer-radio="taskTarget:incoming1"/g) || []).length === 2, 'all candidates are offered');
check(!options.includes(' checked'), 'no candidate is preselected');
check(options.includes('10000 км') && options.includes('20000 км') && options.includes('70000 км'), 'interval and service distinguish candidates');
check(context.transferScreenHtml().includes('data-transfer-action="review" disabled'), 'review is disabled until resolved');
state.taskTargets.incoming1 = 'local2';
check(context.transferTaskChoicesReady(), 'explicit target resolves update');
check(context.transferChosenTask(incoming).id === 'local2', 'concrete target retained');
context.transferGo('review');
const review = context.transferScreenHtml();
check(review.includes('Вариант 2') && review.includes('20000 км') && review.includes('70000 км'), 'preview repeats chosen target details');
state.taskTargets.incoming1 = 'stale';
check(!context.transferTaskChoicesReady(), 'stale IDs never silently select another candidate');
const apply = { dataset: { transferAction: 'apply' }, disabled: false };
context.wireTransfer({ querySelectorAll: selector => selector === '[data-transfer-action]' ? [apply] : [] });
apply.onclick();
check(state.step === 'review', 'apply guard rejects stale target even without button disabled');
state.incoming.cars[0].tasks.push({ id: 'incoming2', name: 'Oil', intervalKm: 25000 });
state.decisions.incoming2 = 'update';
state.taskTargets.incoming1 = 'local1';
state.taskTargets.incoming2 = 'local1';
check(!context.transferTaskChoicesReady(), 'two incoming tasks cannot update the same local task');
state.taskTargets.incoming2 = 'local2';
check(context.transferTaskChoicesReady(), 'one-to-one explicit mappings are accepted');
const separate = { dataset: { transferRadio: 'decision:incoming1' }, value: 'separate' };
context.wireTransfer({ querySelectorAll: selector => selector === '[data-transfer-radio]' ? [separate] : [] });
separate.onchange();
check(!('incoming1' in state.taskTargets), 'changing action clears update target');
const target = { dataset: { transferRadio: 'target' }, value: 'new' };
context.wireTransfer({ querySelectorAll: selector => selector === '[data-transfer-radio]' ? [target] : [] });
target.onchange();
check(Object.keys(state.taskTargets).length === 0 && Object.keys(state.decisions).length === 0, 'target change clears all mappings');
state.target = 'car1';
state.taskTargets.incoming1 = 'local2';
context.transferOpenDemoFile();
check(Object.keys(state.taskTargets).length === 0, 'file change clears mappings');
state.decisions.incoming1 = 'update';
cars[0].tasks = [cars[0].tasks[0]];
check(context.transferChosenTask(incoming).id === 'local1', 'unique candidate is the explicit update target');
state.taskTargets.incoming1 = 'removed';
check(context.transferChosenTask(incoming) === null, 'removed choice cannot fall back to the remaining task');
check(context.transferTaskTargetOptions(incoming).includes('data-transfer-radio="taskTarget:incoming1"'), 'stale choice can be resolved explicitly');
cars[0].tasks[0].name = 'Oil <img>';
state.incoming.cars[0].tasks[0].name = 'Oil <img>';
state.taskTargets.incoming1 = 'removed';
const escaped = context.transferTaskTargetOptions(state.incoming.cars[0].tasks[0]);
check(escaped.includes('Oil &lt;img&gt;') && !escaped.includes('Oil <img>'), 'candidate text is escaped');
console.log(`PASS ${checks} mock task-target checks; inline JavaScript syntax valid; no screenshots or browser/network access.`);
