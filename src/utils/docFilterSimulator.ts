/**
 * موتور شبیه‌ساز پردازش تصویر و فیلتر فتوکپی حرفه‌ای در مرورگر
 * دقیقاً مطابق با پیاده‌سازی کاتلین در DocFilterEngine.kt
 */

export interface ProcessFilterOptions {
  filter: 'photocopy' | 'bw' | 'color' | 'original';
}

/**
 * اعمال فیلتر فتوکپی استودیویی پیشرفته با تصحیح سطح پس‌زمینه (Flatfield)
 * و Post-Sharpening غیرتخریبی همراه با تقویت خوانایی خطوط فارسی
 */
export function processDocumentImage(
  sourceImage: HTMLImageElement | HTMLCanvasElement,
  filter: 'photocopy' | 'bw' | 'color' | 'original'
): string {
  const canvas = document.createElement('canvas');
  const width = sourceImage.width || (sourceImage as HTMLImageElement).naturalWidth || 800;
  const height = sourceImage.height || (sourceImage as HTMLImageElement).naturalHeight || 1100;
  
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  ctx.drawImage(sourceImage, 0, 0, width, height);

  if (filter === 'original') {
    return canvas.toDataURL('image/jpeg', 0.92);
  }

  const imageData = ctx.getImageData(0, 0, width, height);
  const data = imageData.data;
  const totalPixels = width * height;

  if (filter === 'color') {
    // افزایش اشباع رنگی و کنتراست شفاف برای مهرهای رنگی و سربرگ‌ها
    for (let i = 0; i < totalPixels; i++) {
      const idx = i * 4;
      const r = data[idx];
      const g = data[idx + 1];
      const b = data[idx + 2];

      const lum = 0.299 * r + 0.587 * g + 0.114 * b;
      // افزایش اشباع ۱.۴ برابر
      const sat = 1.4;
      let newR = lum + (r - lum) * sat;
      let newG = lum + (g - lum) * sat;
      let newB = lum + (b - lum) * sat;

      // کنتراست ملایم و روشن‌سازی زمینه
      const contrast = 1.25;
      newR = (newR - 128) * contrast + 128 + 14;
      newG = (newG - 128) * contrast + 128 + 14;
      newB = (newB - 128) * contrast + 128 + 14;

      data[idx] = Math.max(0, Math.min(255, newR));
      data[idx + 1] = Math.max(0, Math.min(255, newG));
      data[idx + 2] = Math.max(0, Math.min(255, newB));
    }
    ctx.putImageData(imageData, 0, 0);
    return canvas.toDataURL('image/jpeg', 0.92);
  }

  if (filter === 'bw') {
    // سیاه و سفید استاندارد اسنادی با طیف خاکستری طبیعی
    for (let i = 0; i < totalPixels; i++) {
      const idx = i * 4;
      const r = data[idx];
      const g = data[idx + 1];
      const b = data[idx + 2];
      const lum = 0.299 * r + 0.587 * g + 0.114 * b;
      const val = Math.max(0, Math.min(255, (lum - 128) * 1.3 + 128 + 5));
      data[idx] = val;
      data[idx + 1] = val;
      data[idx + 2] = val;
    }
    ctx.putImageData(imageData, 0, 0);
    return canvas.toDataURL('image/jpeg', 0.92);
  }

  // --- فیلتر فتوکپی استودیویی با الگوریتم Flatfield + Post-Sharpening غیرتخریبی ---
  // ۱. ماتریس روشنایی
  const lum = new Float32Array(totalPixels);
  for (let i = 0; i < totalPixels; i++) {
    const idx = i * 4;
    lum[i] = 0.299 * data[idx] + 0.587 * data[idx + 1] + 0.114 * data[idx + 2];
  }

  // ۲. استخراج سطح روشنایی پس‌زمینه با بسته‌شدن مورفولوژیک (Morphological Closing) در ابعاد کوچک‌شده
  const downScale = Math.max(4, Math.min(16, Math.floor(Math.max(width, height) / 120)));
  const gw = Math.max(8, Math.ceil(width / downScale));
  const gh = Math.max(8, Math.ceil(height / downScale));

  const maxGrid = new Float32Array(gw * gh);
  for (let gy = 0; gy < gh; gy++) {
    const y0 = gy * downScale;
    const y1 = Math.min(y0 + downScale, height);
    for (let gx = 0; gx < gw; gx++) {
      const x0 = gx * downScale;
      const x1 = Math.min(x0 + downScale, width);

      let maxVal = 0;
      const stepY = Math.max(1, Math.floor((y1 - y0) / 4));
      const stepX = Math.max(1, Math.floor((x1 - x0) / 4));

      for (let y = y0; y < y1; y += stepY) {
        const row = y * width;
        for (let x = x0; x < x1; x += stepX) {
          const v = lum[row + x];
          if (v > maxVal) maxVal = v;
        }
      }
      maxGrid[gy * gw + gx] = Math.max(40, Math.min(255, maxVal));
    }
  }

  // اتساع (Dilation) برای حذف خطوط متن
  const dilated = new Float32Array(gw * gh);
  const dilateRadius = 2;
  for (let gy = 0; gy < gh; gy++) {
    const minY = Math.max(0, gy - dilateRadius);
    const maxY = Math.min(gh - 1, gy + dilateRadius);
    for (let gx = 0; gx < gw; gx++) {
      const minX = Math.max(0, gx - dilateRadius);
      const maxX = Math.min(gw - 1, gx + dilateRadius);

      let maxV = 0;
      for (let y = minY; y <= maxY; y++) {
        const r = y * gw;
        for (let x = minX; x <= maxX; x++) {
          const v = maxGrid[r + x];
          if (v > maxV) maxV = v;
        }
      }
      dilated[gy * gw + gx] = maxV;
    }
  }

  // فرسایش (Erosion)
  const closed = new Float32Array(gw * gh);
  for (let gy = 0; gy < gh; gy++) {
    const minY = Math.max(0, gy - dilateRadius);
    const maxY = Math.min(gh - 1, gy + dilateRadius);
    for (let gx = 0; gx < gw; gx++) {
      const minX = Math.max(0, gx - dilateRadius);
      const maxX = Math.min(gw - 1, gx + dilateRadius);

      let minV = 255;
      for (let y = minY; y <= maxY; y++) {
        const r = y * gw;
        for (let x = minX; x <= maxX; x++) {
          const v = dilated[r + x];
          if (v < minV) minV = v;
        }
      }
      closed[gy * gw + gx] = minV;
    }
  }

  // هموارسازی ۳x۳ سطح پس‌زمینه
  const bgSurface = new Float32Array(gw * gh);
  for (let gy = 0; gy < gh; gy++) {
    const minY = Math.max(0, gy - 1);
    const maxY = Math.min(gh - 1, gy + 1);
    for (let gx = 0; gx < gw; gx++) {
      const minX = Math.max(0, gx - 1);
      const maxX = Math.min(gw - 1, gx + 1);

      let sum = 0;
      let count = 0;
      for (let y = minY; y <= maxY; y++) {
        const r = y * gw;
        for (let x = minX; x <= maxX; x++) {
          sum += closed[r + x];
          count++;
        }
      }
      bgSurface[gy * gw + gx] = sum / count;
    }
  }

  // ۳. نگاشت تونال پیوسته سیگموئید با درونیابی دوخطی نور موضعی
  const toneBuffer = new Uint8Array(totalPixels);

  for (let y = 0; y < height; y++) {
    const fy = y / downScale;
    const gy0 = Math.max(0, Math.min(gh - 1, Math.floor(fy)));
    const gy1 = Math.min(gy0 + 1, gh - 1);
    const wy = Math.max(0, Math.min(1, fy - gy0));
    const rowOffset = y * width;

    for (let x = 0; x < width; x++) {
      const fx = x / downScale;
      const gx0 = Math.max(0, Math.min(gw - 1, Math.floor(fx)));
      const gx1 = Math.min(gx0 + 1, gw - 1);
      const wx = Math.max(0, Math.min(1, fx - gx0));

      const top = bgSurface[gy0 * gw + gx0] * (1 - wx) + bgSurface[gy0 * gw + gx1] * wx;
      const bottom = bgSurface[gy1 * gw + gx0] * (1 - wx) + bgSurface[gy1 * gw + gx1] * wx;
      const bgLum = Math.max(35, top * (1 - wy) + bottom * wy);

      const idx = rowOffset + x;
      const ratio = Math.max(0, Math.min(1.25, lum[idx] / bgLum));

      let tone: number;
      if (ratio >= 0.84) {
        tone = 255;
      } else if (ratio <= 0.44) {
        const t = Math.max(0, Math.min(1, ratio / 0.44));
        tone = Math.floor(t * 16);
      } else {
        const t = (ratio - 0.44) / (0.84 - 0.44);
        const s = t * t * (3 - 2 * t);
        tone = Math.max(0, Math.min(255, Math.floor(16 + s * 239)));
      }
      toneBuffer[idx] = tone;
    }
  }

  // ۴. فیلتر Post-Sharpening غیرتخریبی، گیت نویز و لکه‌زدایی
  const noiseThreshold = 10;
  for (let y = 0; y < height; y++) {
    const rowOffset = y * width;
    const isBorderY = y === 0 || y === height - 1;

    for (let x = 0; x < width; x++) {
      const idx = rowOffset + x;
      const pIdx = idx * 4;
      const center = toneBuffer[idx];

      if (isBorderY || x === 0 || x === width - 1) {
        const v = center > 210 ? 255 : center;
        data[pIdx] = v;
        data[pIdx + 1] = v;
        data[pIdx + 2] = v;
        data[pIdx + 3] = 255;
        continue;
      }

      const left = toneBuffer[idx - 1];
      const right = toneBuffer[idx + 1];
      const top = toneBuffer[idx - width];
      const bottom = toneBuffer[idx + width];

      // حذف ذرات نویز منفرد (Despeckle)
      if (center > 0 && center < 220 && left > 240 && right > 240 && top > 240 && bottom > 240) {
        data[pIdx] = 255;
        data[pIdx + 1] = 255;
        data[pIdx + 2] = 255;
        data[pIdx + 3] = 255;
        continue;
      }

      const localMean = (left + right + top + bottom) / 4;
      const diff = center - localMean;
      let enhanced = center;

      if (Math.abs(diff) > noiseThreshold) {
        const boost = diff > 0 ? (diff - noiseThreshold) * 0.45 : (diff + noiseThreshold) * 0.55;
        enhanced = Math.max(0, Math.min(255, Math.floor(center + boost)));
      }

      // تقویت خطوط پیوسته حروف فارسی
      const isHorizontalStroke = left < 60 && right < 60;
      const isVerticalStroke = top < 60 && bottom < 60;
      if ((isHorizontalStroke || isVerticalStroke) && enhanced >= 60 && enhanced <= 180) {
        enhanced = Math.floor(enhanced * 0.55);
      }

      let finalV = enhanced;
      if (finalV >= 240) finalV = 255;
      else if (finalV <= 30) finalV = Math.floor(finalV / 2);

      data[pIdx] = finalV;
      data[pIdx + 1] = finalV;
      data[pIdx + 2] = finalV;
      data[pIdx + 3] = 255;
    }
  }

  ctx.putImageData(imageData, 0, 0);
  return canvas.toDataURL('image/jpeg', 0.92);
}
