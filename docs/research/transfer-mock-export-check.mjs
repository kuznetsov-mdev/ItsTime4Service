import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const start = html.indexOf('  var transferState = null;');
const end = html.indexOf('  function paintInteractive()', start);
assert.ok(start >= 0 && end > start);
const cars = [{
    id: 'test-car', brand: 'Test car', mileageKm: 90000, vin: 'TEST', plate: 'TEST',
    tasks: [
        { id: 'oil', name: 'Oil', intervalKm: 10000, lastKm: 80000, lastDate: '2026-10-01' },
        { id: 'air', name: 'Air filter', intervalKm: 15000 },
    ],
    history: [{ taskName: 'Oil', mileageKm: 80000, date: '2026-10-01', costRub: 500, comment: 'Test' }],
}];
const context = vm.createContext({
    cars, soldCars: [], istate: { detailId: 'test-car' },
    carById: id => cars.find(car => car.id === id), fmtKm: value => `${value} км`,
    icon: () => '', paintInteractive: () => {},
});
vm.runInContext(html.slice(start, end), context);

function trigger(selector, event, element, syncCarForm) {
    context.wireTransfer({ querySelectorAll: query => query === selector ? [element] : [] }, syncCarForm);
    element[event]();
}
function action(transferAction) {
    trigger('[data-transfer-action]', 'onclick', { dataset: { transferAction } });
}
function toggle(transferCheck, checked) {
    trigger('[data-transfer-check]', 'onchange', { dataset: { transferCheck }, checked });
}
function checkSettings() {
    const screen = context.transferScreenHtml();
    assert.ok(screen.includes('<div class="s-title">Экспорт</div>'));
    assert.match(screen, /<div class="form-body transfer-body"><div class="transfer-group"><div class="settings-group-title">Защита паролем<\/div>/);
    assert.match(screen, /<div class="transfer-footer"><button[^>]*data-transfer-action="checkExport">Продолжить<\/button>/);
    return screen;
}

trigger('[data-open-transfer]', 'onclick', { dataset: { openTransfer: 'car' } });
const state = context.transferState;
assert.equal(state.step, 'exportPurpose');
assert.match(context.transferScreenHtml(), /Хочу сохранить резервную копию авто/);
assert.match(context.transferScreenHtml(), /Хочу поделиться регламентом с другим автовладельцем/);
action('carBackup');
const backupScreen = checkSettings();
assert.match(backupScreen, /data-transfer-check="group:service" checked/);
assert.match(backupScreen, /data-transfer-check="group:details" checked/);
assert.match(backupScreen, /data-transfer-check="allExportTasks" checked/);
assert.doesNotMatch(backupScreen, /data-transfer-select="carId"/);
assert.equal(state.protected, true);
const backup = context.transferBuildDemoFile('car', true);
assert.equal(backup.cars.length, 1);
assert.equal(backup.cars[0].tasks[0].lastKm, 80000);
assert.equal(backup.cars[0].tasks[0].lastDate, '2026-10-01');
assert.equal(backup.cars[0].history[0].costRub, 500);
toggle('group:details', false);
assert.ok(!('costRub' in context.transferBuildDemoFile('car', true).cars[0].history[0]));
toggle('group:details', true);
toggle('group:history', false);
assert.doesNotMatch(context.transferScreenHtml(), /data-transfer-check="group:details"/);
assert.ok(!('history' in context.transferBuildDemoFile('car', true).cars[0]));
assert.equal(context.transferBuildDemoFile('car', true).cars[0].tasks[0].lastKm, 80000);
toggle('protected', false);
assert.equal(state.modal, 'plainExport');
assert.equal(state.protected, true);
action('keepProtection');
assert.equal(state.modal, null);
assert.equal(state.protected, true);
state.description = 'A backup draft';
state.password = state.repeat = 'test password';
toggle('task:oil', false);
action('checkExport');
assert.equal(state.step, 'exportReview');
action('back');
assert.equal(state.selected.oil, false);
action('back');
action('share');
const shareScreen = checkSettings();
assert.doesNotMatch(shareScreen, /data-transfer-check="group:/);
assert.doesNotMatch(shareScreen, /data-transfer-field="password"/);
assert.match(shareScreen, /data-transfer-check="allExportTasks" checked/);
assert.equal(state.description, '');
assert.equal(state.password, '');
toggle('allExportTasks', false);
assert.equal(context.transferBuildDemoFile('car', false).cars[0].tasks.length, 0);
toggle('task:oil', true);
toggle('group:service', true);
action('checkExport');
assert.equal(state.step, 'exportReview');
action('send');
action('saved');
assert.equal(state.success, 'share');
const shared = context.transferDemoFile;
assert.equal(Object.keys(shared.groups).length, 0);
assert.deepEqual(Object.keys(shared.cars[0]), ['brand', 'tasks']);
assert.equal(shared.cars[0].tasks.length, 1);
assert.ok(!('lastKm' in shared.cars[0].tasks[0]));
assert.ok(!('lastDate' in shared.cars[0].tasks[0]));
action('finish');
assert.equal(context.istate.screen, 'detail');

trigger('[data-open-transfer]', 'onclick', { dataset: { openTransfer: 'car' } });
action('carBackup');
action('back');
assert.equal(context.transferState.step, 'exportPurpose');
action('back');
assert.equal(context.istate.screen, 'detail');
assert.equal(context.transferScreenHtml(), '');

trigger('[data-open-transfer]', 'onclick', { dataset: { openTransfer: 'settings' } });
action('exportPurpose');
action('carBackup');
assert.match(checkSettings(), /data-transfer-select="carId"/);
action('back');
action('back');
action('backup');
const full = context.transferBuildDemoFile('backup', true);
assert.equal(full.kind, 'backup');
assert.equal(full.cars[0].history[0].costRub, 500);

const draft = { brand: 'Draft vehicle', mileage: '80000' };
const tasks = [{ n: 1, name: 'Draft service' }];
function openImportFromCarForm() {
    trigger('[data-open-transfer]', 'onclick', { dataset: { openTransfer: 'new' } }, () => {
        assert.equal(context.istate.screen, 'new');
        context.istate.form = draft;
        context.istate.taskBlocks = tasks;
    });
}
context.istate.screen = 'new';
openImportFromCarForm();
assert.equal(context.transferState.step, 'files');
action('chooseFile');
action('cancelPicker');
assert.equal(context.transferState.step, 'files');
action('back');
assert.equal(context.istate.screen, 'new');
assert.equal(context.istate.form, draft);
assert.equal(context.istate.taskBlocks, tasks);

for (const finishAction of ['finish', 'back']) {
    context.istate.screen = 'new';
    openImportFromCarForm();
    action('chooseFile');
    toggle('pickerSelected', true);
    action('openFile');
    assert.equal(context.transferState.step, 'preview');
    action('previewNext');
    trigger('[data-transfer-radio]', 'onchange', { dataset: { transferRadio: 'target' }, value: 'new' });
    context.transferState.mileage = '120000';
    action('targetNext');
    assert.equal(context.transferState.step, 'review');
    action('apply');
    assert.equal(context.transferState.step, 'success');
    action(finishAction);
    assert.equal(context.istate.screen, 'cars');
    assert.equal(context.transferState, null);
}

console.log('PASS mock export composition and navigation, footer, and import from the add-car form.');
