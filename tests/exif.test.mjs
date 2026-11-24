import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/exif.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 },
}).outputText;
const { readCaptureDate } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);

function makeJpeg({ ifdDate = null, originalDate = null } = {}) {
  const tiff = new Uint8Array(180);
  const view = new DataView(tiff.buffer);
  const ascii = (offset, value) => {
    for (let i = 0; i < value.length; i++) tiff[offset + i] = value.charCodeAt(i);
  };

  ascii(0, 'II');
  view.setUint16(2, 42, true);
  view.setUint32(4, 8, true);
  view.setUint16(8, ifdDate ? 2 : 1, true);

  let entry = 10;
  view.setUint16(entry, 0x8769, true);
  view.setUint16(entry + 2, 4, true);
  view.setUint32(entry + 4, 1, true);
  view.setUint32(entry + 8, 64, true);

  if (ifdDate) {
    entry += 12;
    view.setUint16(entry, 0x0132, true);
    view.setUint16(entry + 2, 2, true);
    view.setUint32(entry + 4, 20, true);
    view.setUint32(entry + 8, 100, true);
    ascii(100, `${ifdDate} 00:00:00\0`);
  }

  view.setUint16(64, originalDate ? 1 : 0, true);
  if (originalDate) {
    view.setUint16(66, 0x9003, true);
    view.setUint16(68, 2, true);
    view.setUint32(70, 20, true);
    view.setUint32(74, 120, true);
    ascii(120, `${originalDate} 00:00:00\0`);
  }

  const exif = new Uint8Array(6 + tiff.length);
  exif.set([0x45, 0x78, 0x69, 0x66, 0, 0]);
  exif.set(tiff, 6);
  const jpeg = new Uint8Array(2 + 2 + 2 + exif.length);
  jpeg.set([0xff, 0xd8, 0xff, 0xe1], 0);
  new DataView(jpeg.buffer).setUint16(4, exif.length + 2, false);
  jpeg.set(exif, 6);
  return jpeg;
}

function asFile(bytes, name = 'memory.jpg', type = 'image/jpeg') {
  return {
    name,
    type,
    size: bytes.length,
    slice(start, end) {
      return new Blob([bytes.slice(start, end)]);
    },
  };
}

test('reads DateTimeOriginal when IFD0 has no date', async () => {
  assert.equal(await readCaptureDate(asFile(makeJpeg({ originalDate: '2024:03:17' }))), '2024-03-17');
});

test('prefers DateTimeOriginal when both EXIF dates exist', async () => {
  assert.equal(await readCaptureDate(asFile(makeJpeg({
    ifdDate: '2023:01:02',
    originalDate: '2024:03:17',
  }))), '2024-03-17');
});

test('falls back to IFD0 DateTime when DateTimeOriginal is absent', async () => {
  assert.equal(await readCaptureDate(asFile(makeJpeg({ ifdDate: '2022:11:09' }))), '2022-11-09');
});

test('returns null for truncated EXIF data instead of throwing', async () => {
  const truncated = makeJpeg({ originalDate: '2024:03:17' }).slice(0, 24);
  assert.equal(await readCaptureDate(asFile(truncated)), null);
});

test('returns null for unsupported non-JPEG files', async () => {
  assert.equal(await readCaptureDate(asFile(new Uint8Array([1, 2, 3]), 'memory.png', 'image/png')), null);
});
