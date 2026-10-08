// Лише локальні Firestore/Storage-емулятори. Жодних акаунтів чи записів у живому Firebase.
const fs = require('node:fs');
const path = require('node:path');
const root = process.env.FIREBASE_RULES_TEST_DEPS;
if (!root) throw new Error('Set FIREBASE_RULES_TEST_DEPS to the directory containing node_modules');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require(path.join(root, 'node_modules/@firebase/rules-unit-testing'));

async function main() {
  const workspace = path.resolve(__dirname, '..');
  const environment = await initializeTestEnvironment({ projectId: 'demo-nahadaika-storage',
    firestore: { host: '127.0.0.1', port: 8781, rules: fs.readFileSync(path.join(workspace, 'firebase/firestore.rules'), 'utf8') },
    storage: { host: '127.0.0.1', port: 9191, rules: fs.readFileSync(path.join(workspace, 'firebase/storage.rules'), 'utf8') } });
  let passed = 0;
  async function check(name, promise, allowed) {
    await (allowed ? assertSucceeds(promise) : assertFails(promise));
    passed++;
    console.log(`OK ${name}`);
  }
  try {
    await environment.withSecurityRulesDisabled(async context => {
      await context.firestore().doc('chats/family').set({ name: 'Сімʼя', members: ['owner', 'member'], names: { owner: 'Я', member: 'Рідні' }, owner: 'owner', inviteCode: 'ABCDEF' });
      await context.firestore().doc('chats/other').set({ name: 'Інша сімʼя', members: ['stranger'], names: { stranger: 'Чужий' }, owner: 'stranger', inviteCode: 'FEDCBA' });
    });
    const owner = environment.authenticatedContext('owner').storage();
    const member = environment.authenticatedContext('member').storage();
    const stranger = environment.authenticatedContext('stranger').storage();
    const anonymous = environment.unauthenticatedContext().storage();
    const file = 'chats/family/media/voice.m4a';
    const bytes = Uint8Array.from([1, 2, 3, 4]);
    await check('учасник додає голосове', owner.ref(file).put(bytes, { contentType: 'audio/mp4' }), true);
    await check('інший учасник читає медіа', member.ref(file).getMetadata(), true);
    await check('неавторизований не читає', anonymous.ref(file).getMetadata(), false);
    await check('сторонній не читає', stranger.ref(file).getMetadata(), false);
    await check('сторонній не пише', stranger.ref(file).put(bytes, { contentType: 'audio/mp4' }), false);
    await check('учасник не пише в інший чат', owner.ref('chats/other/media/x.jpg').put(bytes, { contentType: 'image/jpeg' }), false);
    await check('непідтримуваний MIME відхиляється', owner.ref('chats/family/media/x.exe').put(bytes, { contentType: 'application/octet-stream' }), false);
    await check('поза каталогом медіа писати не можна', owner.ref('chats/family/arbitrary.jpg').put(bytes, { contentType: 'image/jpeg' }), false);
    await check('порожній файл відхиляється', owner.ref('chats/family/media/empty.jpg').put(new Uint8Array(0), { contentType: 'image/jpeg' }), false);
    await check('завеликий файл відхиляється', owner.ref('chats/family/media/large.mp4').put(new Uint8Array(25 * 1024 * 1024 + 1), { contentType: 'video/mp4' }), false);
    await environment.withSecurityRulesDisabled(async context => {
      await context.firestore().doc('chats/family').update({ members: ['owner'] });
    });
    await check('учасник після виходу не читає', member.ref(file).getMetadata(), false);
    await check('учасник після виходу не видаляє', member.ref(file).delete(), false);
    console.log(`Storage rules: ${passed}/${passed} passed.`);
  } finally {
    await environment.cleanup();
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
