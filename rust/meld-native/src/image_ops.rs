use std::fs;

/// Losslessly strips EXIF/GPS/XMP/IPTC/comment metadata directly from JPEG, PNG, or WebP
/// container bytes without re-encoding image pixels.
///
/// Returns the number of metadata segments/chunks stripped.
pub fn strip_image_metadata_lossless(input_path: &str, output_path: &str) -> Result<usize, String> {
    let bytes =
        fs::read(input_path).map_err(|e| format!("Failed to read image '{}': {}", input_path, e))?;

    let (cleaned, stripped) = if bytes.len() >= 2 && bytes[0] == 0xFF && bytes[1] == 0xD8 {
        strip_jpeg_metadata(&bytes)?
    } else if bytes.len() >= 8 && &bytes[0..8] == b"\x89PNG\r\n\x1a\n" {
        strip_png_metadata(&bytes)?
    } else if bytes.len() >= 12 && &bytes[0..4] == b"RIFF" && &bytes[8..12] == b"WEBP" {
        strip_webp_metadata(&bytes)?
    } else {
        return Err("Unsupported format for lossless container metadata strip".to_string());
    };

    fs::write(output_path, &cleaned)
        .map_err(|e| format!("Failed to write sanitized image '{}': {}", output_path, e))?;
    Ok(stripped)
}

fn strip_jpeg_metadata(bytes: &[u8]) -> Result<(Vec<u8>, usize), String> {
    let mut out = Vec::with_capacity(bytes.len());
    out.extend_from_slice(&bytes[0..2]); // SOI
    let mut pos = 2usize;
    let mut stripped = 0usize;

    while pos < bytes.len() {
        if bytes[pos] != 0xFF {
            return Err("Malformed JPEG marker stream".to_string());
        }
        while pos < bytes.len() && bytes[pos] == 0xFF {
            pos += 1;
        }
        if pos >= bytes.len() {
            break;
        }
        let marker = bytes[pos];
        pos += 1;

        // EOI
        if marker == 0xD9 {
            out.push(0xFF);
            out.push(0xD9);
            break;
        }

        // SOS: copy marker and entire entropy-coded scan through end of stream untouched
        if marker == 0xDA {
            out.push(0xFF);
            out.push(0xDA);
            out.extend_from_slice(&bytes[pos..]);
            break;
        }

        // Standalone markers without length field (RST0..RST7, TEM)
        if (0xD0..=0xD7).contains(&marker) || marker == 0x01 {
            out.push(0xFF);
            out.push(marker);
            continue;
        }

        if pos + 2 > bytes.len() {
            return Err("Truncated JPEG segment length".to_string());
        }
        let seg_len = u16::from_be_bytes([bytes[pos], bytes[pos + 1]]) as usize;
        if seg_len < 2 || pos + seg_len > bytes.len() {
            return Err("Invalid JPEG segment length".to_string());
        }
        let payload = &bytes[pos + 2..pos + seg_len];

        let should_strip = match marker {
            0xE1 | 0xED | 0xEE | 0xFE => true, // APP1 (EXIF/XMP), APP13 (IPTC), APP14, COM
            0xE2 => !payload.starts_with(b"ICC_PROFILE\0"), // Preserve ICC color profiles
            0xE3..=0xEC | 0xEF => true,        // Other vendor APPn metadata segments
            _ => false,
        };

        if should_strip {
            stripped += 1;
        } else {
            out.push(0xFF);
            out.push(marker);
            out.extend_from_slice(&bytes[pos..pos + seg_len]);
        }

        pos += seg_len;
    }

    Ok((out, stripped))
}

fn strip_png_metadata(bytes: &[u8]) -> Result<(Vec<u8>, usize), String> {
    let mut out = Vec::with_capacity(bytes.len());
    out.extend_from_slice(&bytes[0..8]); // PNG signature
    let mut pos = 8usize;
    let mut stripped = 0usize;

    while pos + 12 <= bytes.len() {
        let len = u32::from_be_bytes([bytes[pos], bytes[pos + 1], bytes[pos + 2], bytes[pos + 3]])
            as usize;
        let chunk_type = &bytes[pos + 4..pos + 8];
        let total = 12usize
            .checked_add(len)
            .ok_or_else(|| "PNG chunk length overflow".to_string())?;
        if pos + total > bytes.len() {
            return Err("Truncated PNG chunk".to_string());
        }

        let is_metadata = matches!(
            chunk_type,
            b"eXIf" | b"tEXt" | b"zTXt" | b"iTXt" | b"tIME"
        );
        if is_metadata {
            stripped += 1;
        } else {
            out.extend_from_slice(&bytes[pos..pos + total]);
        }

        pos += total;
        if chunk_type == b"IEND" {
            break;
        }
    }

    Ok((out, stripped))
}

fn strip_webp_metadata(bytes: &[u8]) -> Result<(Vec<u8>, usize), String> {
    let mut body = Vec::with_capacity(bytes.len());
    let mut pos = 12usize;
    let mut stripped = 0usize;

    while pos + 8 <= bytes.len() {
        let fourcc = &bytes[pos..pos + 4];
        let chunk_size =
            u32::from_le_bytes([bytes[pos + 4], bytes[pos + 5], bytes[pos + 6], bytes[pos + 7]])
                as usize;
        let padded_size = chunk_size + (chunk_size & 1);
        if pos + 8 + chunk_size > bytes.len() {
            return Err("Truncated WebP RIFF chunk".to_string());
        }

        if fourcc == b"EXIF" || fourcc == b"XMP " {
            stripped += 1;
        } else if fourcc == b"VP8X" && chunk_size >= 1 {
            body.extend_from_slice(fourcc);
            body.extend_from_slice(&(chunk_size as u32).to_le_bytes());
            let mut payload = bytes[pos + 8..pos + 8 + chunk_size].to_vec();
            // Clear EXIF (bit 3 = 0x08) and XMP (bit 2 = 0x04) feature flags
            payload[0] &= !(0x08 | 0x04);
            body.extend_from_slice(&payload);
            if chunk_size & 1 == 1 {
                body.push(0);
            }
        } else {
            let end = (pos + 8 + padded_size).min(bytes.len());
            body.extend_from_slice(&bytes[pos..end]);
            if (chunk_size & 1 == 1) && (pos + 8 + padded_size > bytes.len()) {
                body.push(0);
            }
        }

        pos = pos.saturating_add(8 + padded_size);
    }

    let riff_size = (4usize + body.len()) as u32;
    let mut out = Vec::with_capacity(12 + body.len());
    out.extend_from_slice(b"RIFF");
    out.extend_from_slice(&riff_size.to_le_bytes());
    out.extend_from_slice(b"WEBP");
    out.extend_from_slice(&body);
    Ok((out, stripped))
}

#[inline]
fn color_distance(r1: i32, g1: i32, b1: i32, r2: i32, g2: i32, b2: i32) -> f64 {
    let dr = (r1 - r2) as f64;
    let dg = (g1 - g2) as f64;
    let db = (b1 - b2) as f64;
    (dr * dr + dg * dg + db * db).sqrt()
}

/// Native off-heap edge-seeded BFS flood-fill background removal with soft contour feathering.
/// Algorithmic parity with `ImageEngine.applyFloodFillBackgroundRemoval`.
///
/// Mutates ARGB `pixels` in place and returns total fully-transparent pixel count.
pub fn remove_background_rgba(
    pixels: &mut [i32],
    width: usize,
    height: usize,
    tolerance: i32,
) -> usize {
    if width == 0 || height == 0 || pixels.len() != width.saturating_mul(height) {
        return 0;
    }

    let mut opaque_border = Vec::with_capacity((2 * (width + height)).min(256));
    for x in 0..width {
        if (((pixels[x] as u32) >> 24) & 0xFF) > 20 {
            opaque_border.push(x);
        }
        if height > 1 {
            let bottom_idx = (height - 1) * width + x;
            if (((pixels[bottom_idx] as u32) >> 24) & 0xFF) > 20 {
                opaque_border.push(bottom_idx);
            }
        }
    }
    for y in 1..height.saturating_sub(1) {
        let left_idx = y * width;
        if (((pixels[left_idx] as u32) >> 24) & 0xFF) > 20 {
            opaque_border.push(left_idx);
        }
        if width > 1 {
            let right_idx = y * width + (width - 1);
            if (((pixels[right_idx] as u32) >> 24) & 0xFF) > 20 {
                opaque_border.push(right_idx);
            }
        }
    }

    let (bg_r, bg_g, bg_b) = if opaque_border.is_empty() {
        (255i32, 255i32, 255i32)
    } else {
        let step = (opaque_border.len() / 128).max(1);
        let mut rs = Vec::with_capacity(128);
        let mut gs = Vec::with_capacity(128);
        let mut bs = Vec::with_capacity(128);
        let mut idx = 0usize;
        while idx < opaque_border.len() {
            let c = pixels[opaque_border[idx]] as u32;
            rs.push(((c >> 16) & 0xFF) as i32);
            gs.push(((c >> 8) & 0xFF) as i32);
            bs.push((c & 0xFF) as i32);
            idx += step;
        }
        rs.sort_unstable();
        gs.sort_unstable();
        bs.sort_unstable();
        (rs[rs.len() / 2], gs[gs.len() / 2], bs[bs.len() / 2])
    };

    let hard_threshold = tolerance.clamp(12, 120) as f64;
    let max_gradient_drift = hard_threshold * 1.65;
    let local_step_limit = (hard_threshold * 0.40).max(10.0);
    let soft_threshold = hard_threshold * 1.55;

    let n = pixels.len();
    let mut is_background = vec![false; n];
    let mut queue = Vec::with_capacity(n.min(65536));

    let try_seed_border = |idx: usize,
                           is_bg: &mut [bool],
                           q: &mut Vec<usize>,
                           px: &[i32]| {
        if is_bg[idx] {
            return;
        }
        let c = px[idx] as u32;
        let alpha = (c >> 24) & 0xFF;
        if alpha == 0 {
            is_bg[idx] = true;
            q.push(idx);
            return;
        }
        let r = ((c >> 16) & 0xFF) as i32;
        let g = ((c >> 8) & 0xFF) as i32;
        let b = (c & 0xFF) as i32;
        if color_distance(r, g, b, bg_r, bg_g, bg_b) <= hard_threshold {
            is_bg[idx] = true;
            q.push(idx);
        }
    };

    for x in 0..width {
        try_seed_border(x, &mut is_background, &mut queue, pixels);
        if height > 1 {
            try_seed_border((height - 1) * width + x, &mut is_background, &mut queue, pixels);
        }
    }
    for y in 1..height.saturating_sub(1) {
        try_seed_border(y * width, &mut is_background, &mut queue, pixels);
        if width > 1 {
            try_seed_border(y * width + (width - 1), &mut is_background, &mut queue, pixels);
        }
    }

    let mut head = 0usize;
    while head < queue.len() {
        let curr = queue[head];
        head += 1;
        let pc = pixels[curr] as u32;
        let p_opaque = ((pc >> 24) & 0xFF) > 20;
        let pr = ((pc >> 16) & 0xFF) as i32;
        let pg = ((pc >> 8) & 0xFF) as i32;
        let pb = (pc & 0xFF) as i32;
        let x = curr % width;
        let y = curr / width;

        let mut neighbors = [usize::MAX; 4];
        let mut ncount = 0usize;
        if x > 0 {
            neighbors[ncount] = curr - 1;
            ncount += 1;
        }
        if x + 1 < width {
            neighbors[ncount] = curr + 1;
            ncount += 1;
        }
        if y > 0 {
            neighbors[ncount] = curr - width;
            ncount += 1;
        }
        if y + 1 < height {
            neighbors[ncount] = curr + width;
            ncount += 1;
        }

        for &next_idx in &neighbors[..ncount] {
            if is_background[next_idx] {
                continue;
            }
            let nc = pixels[next_idx] as u32;
            let na = (nc >> 24) & 0xFF;
            if na == 0 {
                is_background[next_idx] = true;
                queue.push(next_idx);
                continue;
            }
            let nr = ((nc >> 16) & 0xFF) as i32;
            let ng = ((nc >> 8) & 0xFF) as i32;
            let nb = (nc & 0xFF) as i32;
            let dist_ref = color_distance(nr, ng, nb, bg_r, bg_g, bg_b);
            let matches = dist_ref <= hard_threshold
                || (p_opaque
                    && dist_ref <= max_gradient_drift
                    && color_distance(nr, ng, nb, pr, pg, pb) <= local_step_limit);
            if matches {
                is_background[next_idx] = true;
                queue.push(next_idx);
            }
        }
    }

    let mut transparent_count = 0usize;
    let feather_low = hard_threshold * 0.55;
    let feather_span = (soft_threshold - feather_low).max(1.0);

    for i in 0..n {
        if is_background[i] {
            pixels[i] = 0;
            transparent_count += 1;
            continue;
        }
        let c = pixels[i] as u32;
        let existing_alpha = ((c >> 24) & 0xFF) as i32;
        if existing_alpha == 0 {
            transparent_count += 1;
            continue;
        }
        let x = i % width;
        let y = i / width;
        let touches_bg = (x > 0 && is_background[i - 1])
            || (x + 1 < width && is_background[i + 1])
            || (y > 0 && is_background[i - width])
            || (y + 1 < height && is_background[i + width]);
        if touches_bg {
            let r = ((c >> 16) & 0xFF) as i32;
            let g = ((c >> 8) & 0xFF) as i32;
            let b = (c & 0xFF) as i32;
            let dist = color_distance(r, g, b, bg_r, bg_g, bg_b);
            if dist < soft_threshold {
                let ratio = ((dist - feather_low) / feather_span).clamp(0.18, 1.0);
                let new_alpha = existing_alpha.min((ratio * existing_alpha as f64) as i32) as u32;
                pixels[i] = ((new_alpha << 24) | (c & 0x00FF_FFFF)) as i32;
            }
        }
    }

    transparent_count
}
