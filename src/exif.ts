/** Reads JPEG EXIF capture date when present. Returns null for formats without supported EXIF metadata. */
export async function readCaptureDate(file: File): Promise<string | null> {
  if (!file.type.toLowerCase().includes('jpeg') && !file.name.toLowerCase().match(/\.jpe?g$/)) return null;
  const bytes = new Uint8Array(await file.slice(0, Math.min(file.size, 2_000_000)).arrayBuffer());
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (view.byteLength < 4 || view.getUint16(0, false) !== 0xffd8) return null;

  const ascii = (offset: number, length: number) => String.fromCharCode(...bytes.slice(offset, offset + length));
  const readAsciiDate = (tiff: number, little: boolean, ifdOffset: number): string | null => {
    const ifd = tiff + ifdOffset;
    if (ifd < tiff || ifd + 2 > view.byteLength) return null;
    const count = view.getUint16(ifd, little);
    for (let i = 0; i < count; i++) {
      const entry = ifd + 2 + i * 12;
      if (entry + 12 > view.byteLength) break;
      const tag = view.getUint16(entry, little);
      if (tag !== 0x9003 && tag !== 0x0132) continue;
      const type = view.getUint16(entry + 2, little);
      const length = view.getUint32(entry + 4, little);
      if (type !== 2 || length < 10 || length > 64) continue;
      const valueOffset = length <= 4 ? entry + 8 : tiff + view.getUint32(entry + 8, little);
      if (valueOffset < tiff || valueOffset + length > view.byteLength) continue;
      const value = ascii(valueOffset, length).replace(/\0.*$/, '');
      const match = /^(\d{4}):(\d{2}):(\d{2})/.exec(value);
      if (match) return `${match[1]}-${match[2]}-${match[3]}`;
    }
    return null;
  };

  let offset = 2;
  while (offset + 4 <= view.byteLength) {
    if (view.getUint8(offset) !== 0xff) break;
    const marker = view.getUint8(offset + 1);
    if (marker === 0xda || marker === 0xd9) break;
    const length = view.getUint16(offset + 2, false);
    const data = offset + 4;
    if (length < 2 || data + length - 2 > view.byteLength) break;
    if (marker === 0xe1 && length >= 14 && ascii(data, 6) === 'Exif\0\0') {
      const tiff = data + 6;
      const order = ascii(tiff, 2);
      const little = order === 'II';
      if (!little && order !== 'MM') return null;
      if (tiff + 8 > view.byteLength || view.getUint16(tiff + 2, little) !== 42) return null;
      const ifd0 = view.getUint32(tiff + 4, little);
      const date = readAsciiDate(tiff, little, ifd0);
      // The Exif sub-IFD may contain DateTimeOriginal even when IFD0 has
      // no DateTime tag. Always inspect its pointer before falling back.
      const ifd = tiff + ifd0;
      if (ifd >= tiff && ifd + 2 <= view.byteLength) {
        const entries = view.getUint16(ifd, little);
        for (let i = 0; i < entries; i++) {
          const entry = ifd + 2 + i * 12;
          if (entry + 12 > view.byteLength) break;
          if (view.getUint16(entry, little) === 0x8769) {
            const originalDate = readAsciiDate(tiff, little, view.getUint32(entry + 8, little));
            if (originalDate) return originalDate;
            break;
          }
        }
      }
      if (date) return date;
    }
    offset = data + length - 2;
  }
  return null;
}
