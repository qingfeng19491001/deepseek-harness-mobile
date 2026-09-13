import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { cameraPicker, PickerMediaType } from '@kit.CameraKit';
import { image } from '@kit.ImageKit';
import { fileIo } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { BusinessError } from '@kit.BasicServicesKit';
import { KRAny } from '@kuikly-open/render';
import { jsonNumber, moduleParams } from './DshJson';
import { DshOhosContext, dshError } from './DshOhosContext';

interface Limits {
  maxCount: number;
  maxImageBytes: number;
  maxImagePixels: number;
  maxImageDimension: number;
  longEdge: number;
}

export class DshImagePicker {
  static async pickImages(params: KRAny): Promise<Record<string, Object>> {
    const limits = parseLimits(params);
    try {
      const picker = new photoAccessHelper.PhotoViewPicker();
      const result = await picker.select({
        MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
        maxSelectNumber: Math.max(1, limits.maxCount),
      });
      const uris = result.photoUris ?? [];
      if (uris.length === 0) {
        return cancelled();
      }
      return await encodeUris(uris.slice(0, limits.maxCount), limits);
    } catch (error) {
      dshError('pickImages failed', error as Object);
      return fail('internal', '无法打开相册');
    }
  }

  static async captureImage(params: KRAny): Promise<Record<string, Object>> {
    const limits = parseLimits(params);
    try {
      const context = DshOhosContext.require();
      const result = await cameraPicker.pick(context, [PickerMediaType.PHOTO], {
        cameraPosition: cameraPicker.CameraPosition.BACK,
      });
      const uri = result.resultUri;
      if (!uri) {
        return cancelled();
      }
      return await encodeUris([uri], limits);
    } catch (error) {
      const code = (error as BusinessError).code;
      if (code === 201) {
        return fail('permission-denied', '未获得相机权限，请在系统设置中允许后重试');
      }
      dshError('captureImage failed', error as Object);
      return fail('internal', '无法打开相机');
    }
  }
}

async function encodeUris(uris: string[], limits: Limits): Promise<Record<string, Object>> {
  const images: Record<string, Object>[] = [];
  for (const uri of uris) {
    const prepared = await prepare(uri, limits);
    if (prepared) {
      images.push(prepared);
    }
  }
  return {
    ok: true,
    imagesJson: JSON.stringify(images),
    rejectedJson: '[]',
  };
}

async function prepare(uri: string, limits: Limits): Promise<Record<string, Object> | null> {
  try {
    const file = fileIo.openSync(uri, fileIo.OpenMode.READ_ONLY);
    const stat = fileIo.statSync(file.fd);
    const buffer = new ArrayBuffer(stat.size);
    fileIo.readSync(file.fd, buffer);
    fileIo.closeSync(file);
    const bytes = new Uint8Array(buffer);
    const name = uri.substring(uri.lastIndexOf('/') + 1) || 'image.jpg';
    const source = image.createImageSource(bytes.buffer);
    const info = await source.getImageInfo();
    const width = info.size.width;
    const height = info.size.height;
    const mime = info.mimeType || guessMime(name);
    if (mime === 'image/gif' && bytes.byteLength <= limits.maxImageBytes) {
      return jsonImage(name, mime, bytes, width, height, false);
    }
    let targetW = width;
    let targetH = height;
    const longest = Math.max(width, height);
    const targetLong = Math.min(limits.longEdge, limits.maxImageDimension);
    let scale = 1;
    if (longest > targetLong) {
      scale = targetLong / longest;
    }
    if (width * height > limits.maxImagePixels) {
      scale = Math.min(scale, Math.sqrt(limits.maxImagePixels / (width * height)));
    }
    if (scale < 0.999) {
      targetW = Math.max(1, Math.floor(width * scale));
      targetH = Math.max(1, Math.floor(height * scale));
    }
    const pixelMap = await source.createPixelMap({
      desiredSize: { width: targetW, height: targetH },
    });
    const packer = image.createImagePacker();
    const qualities = [85, 70, 55, 40, 28];
    let packed: ArrayBuffer | null = null;
    for (const quality of qualities) {
      const next = await packer.packing(pixelMap, { format: 'image/jpeg', quality });
      if (next.byteLength <= limits.maxImageBytes) {
        packed = next;
        break;
      }
    }
    pixelMap.release();
    packer.release();
    source.release();
    if (!packed) {
      return null;
    }
    const out = new Uint8Array(packed);
    const outName = name.toLowerCase().endsWith('.jpg') || name.toLowerCase().endsWith('.jpeg')
      ? name
      : `${name.replace(/\.[^.]+$/, '')}.jpg`;
    return jsonImage(outName, 'image/jpeg', out, targetW, targetH, true);
  } catch (error) {
    dshError('prepare image failed', error as Object);
    return null;
  }
}

function jsonImage(
  name: String,
  mime: string,
  bytes: Uint8Array,
  width: number,
  height: number,
  compressed: boolean,
): Record<string, Object> {
  const helper = new util.Base64Helper();
  return {
    localId: `${Date.now()}-${Math.floor(Math.random() * 100000)}`,
    name,
    mediaType: mime,
    data: helper.encodeToStringSync(bytes).replace(/\s/g, ''),
    bytes: bytes.byteLength,
    width,
    height,
    compressed,
  };
}

function parseLimits(params: KRAny): Limits {
  const json = moduleParams(params as Object);
  return {
    maxCount: jsonNumber(json, 'maxCount', 20),
    maxImageBytes: jsonNumber(json, 'maxImageBytes', 20 * 1024 * 1024),
    maxImagePixels: jsonNumber(json, 'maxImagePixels', 64000000),
    maxImageDimension: jsonNumber(json, 'maxImageDimension', 8192),
    longEdge: jsonNumber(json, 'longEdge', 2048),
  };
}

function guessMime(name: string): string {
  const ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
  if (ext === 'png') return 'image/png';
  if (ext === 'gif') return 'image/gif';
  if (ext === 'webp') return 'image/webp';
  return 'image/jpeg';
}

function cancelled(): Record<string, Object> {
  return { ok: true, cancelled: true, imagesJson: '[]' };
}

function fail(code: string, message: string): Record<string, Object> {
  return { ok: false, code, message, imagesJson: '[]' };
}
