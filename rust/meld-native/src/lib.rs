pub mod image_ops;
pub mod pdf;

use jni::objects::{JClass, JIntArray, JObjectArray, JString};
use jni::sys::jint;
use jni::JNIEnv;

fn read_jstring(env: &mut JNIEnv, s: &JString) -> Option<String> {
    env.get_string(s).ok().map(|js| js.into())
}

#[no_mangle]
pub extern "system" fn Java_app_meld_android_engine_MeldNativeBridge_nativeMergePdfs(
    mut env: JNIEnv,
    _class: JClass,
    input_paths: JObjectArray,
    output_path: JString,
) -> jint {
    let Some(out) = read_jstring(&mut env, &output_path) else {
        return -1;
    };
    let Ok(len) = env.get_array_length(&input_paths) else {
        return -1;
    };
    if len < 2 {
        return -1;
    }

    let mut paths = Vec::with_capacity(len as usize);
    for i in 0..len {
        let Ok(elem) = env.get_object_array_element(&input_paths, i) else {
            return -1;
        };
        let jstr = JString::from(elem);
        let Some(p) = read_jstring(&mut env, &jstr) else {
            return -1;
        };
        paths.push(p);
    }

    match pdf::merge_pdfs(&paths, &out) {
        Ok(pages) => pages as jint,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "system" fn Java_app_meld_android_engine_MeldNativeBridge_nativeSplitPdf(
    mut env: JNIEnv,
    _class: JClass,
    input_path: JString,
    start_page_1based: jint,
    end_page_1based: jint,
    output_path: JString,
) -> jint {
    let (Some(inp), Some(out)) = (
        read_jstring(&mut env, &input_path),
        read_jstring(&mut env, &output_path),
    ) else {
        return -1;
    };
    let start = start_page_1based.max(1) as u32;
    let end = end_page_1based.max(start as i32) as u32;

    match pdf::split_pdf(&inp, start, end, &out) {
        Ok(pages) => pages as jint,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "system" fn Java_app_meld_android_engine_MeldNativeBridge_nativeSanitizePdf(
    mut env: JNIEnv,
    _class: JClass,
    input_path: JString,
    output_path: JString,
) -> jint {
    let (Some(inp), Some(out)) = (
        read_jstring(&mut env, &input_path),
        read_jstring(&mut env, &output_path),
    ) else {
        return -1;
    };

    match pdf::sanitize_pdf(&inp, &out) {
        Ok(pages) => pages as jint,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "system" fn Java_app_meld_android_engine_MeldNativeBridge_nativeStripImageMetadataLossless(
    mut env: JNIEnv,
    _class: JClass,
    input_path: JString,
    output_path: JString,
) -> jint {
    let (Some(inp), Some(out)) = (
        read_jstring(&mut env, &input_path),
        read_jstring(&mut env, &output_path),
    ) else {
        return -1;
    };

    match image_ops::strip_image_metadata_lossless(&inp, &out) {
        Ok(stripped) => stripped as jint,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "system" fn Java_app_meld_android_engine_MeldNativeBridge_nativeRemoveBackgroundRgba(
    env: JNIEnv,
    _class: JClass,
    pixels: JIntArray,
    width: jint,
    height: jint,
    tolerance: jint,
) -> jint {
    if width <= 0 || height <= 0 {
        return -1;
    }
    let Ok(arr_len) = env.get_array_length(&pixels) else {
        return -1;
    };
    let expected = (width as usize).saturating_mul(height as usize);
    if arr_len as usize != expected {
        return -1;
    }

    let mut buf = vec![0i32; expected];
    if env.get_int_array_region(&pixels, 0, &mut buf).is_err() {
        return -1;
    }

    let transparent = image_ops::remove_background_rgba(&mut buf, width as usize, height as usize, tolerance);

    if env.set_int_array_region(&pixels, 0, &buf).is_err() {
        return -1;
    }
    transparent as jint
}

#[cfg(test)]
mod tests {
    use super::{image_ops, pdf};
    use lopdf::content::{Content, Operation};
    use lopdf::{dictionary, Document, Object, Stream};
    use std::fs;
    use std::path::PathBuf;

    fn temp_path(name: &str) -> PathBuf {
        let mut p = std::env::temp_dir();
        p.push(format!("meld_native_test_{}_{}", std::process::id(), name));
        p
    }

    fn create_vector_pdf(path: &PathBuf, page_texts: &[&str], with_metadata: bool) {
        let mut doc = Document::with_version("1.5");
        let pages_id = doc.new_object_id();
        let font_id = doc.add_object(dictionary! {
            "Type" => "Font",
            "Subtype" => "Type1",
            "BaseFont" => "Helvetica",
        });
        let resources_id = doc.add_object(dictionary! {
            "Font" => dictionary! {
                "F1" => font_id,
            },
        });

        let mut kids = Vec::new();
        for &txt in page_texts {
            let content = Content {
                operations: vec![
                    Operation::new("BT", vec![]),
                    Operation::new("Tf", vec!["F1".into(), 18.into()]),
                    Operation::new("Td", vec![72.into(), 720.into()]),
                    Operation::new("Tj", vec![Object::string_literal(txt)]),
                    Operation::new("ET", vec![]),
                ],
            };
            let content_id = doc.add_object(Stream::new(dictionary! {}, content.encode().unwrap()));
            let page_id = doc.add_object(dictionary! {
                "Type" => "Page",
                "Parent" => pages_id,
                "Contents" => content_id,
            });
            kids.push(Object::Reference(page_id));
        }

        // Put MediaBox and Resources on /Pages to exercise attribute inheritance!
        doc.objects.insert(
            pages_id,
            Object::Dictionary(dictionary! {
                "Type" => "Pages",
                "Kids" => kids,
                "Count" => page_texts.len() as i64,
                "MediaBox" => vec![0.into(), 0.into(), 595.into(), 842.into()],
                "Resources" => resources_id,
            }),
        );

        let mut catalog = dictionary! {
            "Type" => "Catalog",
            "Pages" => pages_id,
        };
        if with_metadata {
            let xmp_id = doc.add_object(Stream::new(
                dictionary! {
                    "Type" => "Metadata",
                    "Subtype" => "XML",
                },
                b"<x:xmpmeta>SecretAuthorXMP</x:xmpmeta>".to_vec(),
            ));
            catalog.set("Metadata", Object::Reference(xmp_id));
            let info_id = doc.add_object(dictionary! {
                "Author" => Object::string_literal("Confidential Author"),
                "Producer" => Object::string_literal("Private Producer"),
            });
            doc.trailer.set("Info", Object::Reference(info_id));
        }
        let catalog_id = doc.add_object(catalog);
        doc.trailer.set("Root", catalog_id);
        doc.save(path).unwrap();
    }

    #[test]
    fn lossless_pdf_merge_split_and_sanitize_preserve_vector_text() {
        let pdf_a = temp_path("a.pdf");
        let pdf_b = temp_path("b.pdf");
        let merged = temp_path("merged.pdf");
        let split = temp_path("split.pdf");
        let sanitized = temp_path("sanitized.pdf");

        create_vector_pdf(&pdf_a, &["Alpha Page 1", "Alpha Page 2"], true);
        create_vector_pdf(&pdf_b, &["Beta Page 3"], false);

        let merged_pages = pdf::merge_pdfs(
            &[
                pdf_a.to_string_lossy().into_owned(),
                pdf_b.to_string_lossy().into_owned(),
            ],
            &merged.to_string_lossy(),
        )
        .unwrap();
        assert_eq!(merged_pages, 3);

        let loaded_merged = Document::load(&merged).unwrap();
        assert_eq!(loaded_merged.get_pages().len(), 3);
        let t1 = loaded_merged.extract_text(&[1]).unwrap();
        let t3 = loaded_merged.extract_text(&[3]).unwrap();
        assert!(t1.contains("Alpha Page 1"), "page 1 text={}", t1);
        assert!(t3.contains("Beta Page 3"), "page 3 text={}", t3);

        let split_pages =
            pdf::split_pdf(&merged.to_string_lossy(), 2, 3, &split.to_string_lossy()).unwrap();
        assert_eq!(split_pages, 2);
        let loaded_split = Document::load(&split).unwrap();
        assert_eq!(loaded_split.get_pages().len(), 2);
        assert!(loaded_split.extract_text(&[1]).unwrap().contains("Alpha Page 2"));
        assert!(loaded_split.extract_text(&[2]).unwrap().contains("Beta Page 3"));

        let san_pages =
            pdf::sanitize_pdf(&pdf_a.to_string_lossy(), &sanitized.to_string_lossy()).unwrap();
        assert_eq!(san_pages, 2);
        let raw_sanitized = fs::read(&sanitized).unwrap();
        let raw_str = String::from_utf8_lossy(&raw_sanitized);
        assert!(!raw_str.contains("Confidential Author"));
        assert!(!raw_str.contains("SecretAuthorXMP"));
        let loaded_san = Document::load(&sanitized).unwrap();
        assert!(loaded_san.trailer.get(b"Info").is_err());
        assert!(loaded_san.extract_text(&[1]).unwrap().contains("Alpha Page 1"));

        for p in [pdf_a, pdf_b, merged, split, sanitized] {
            let _ = fs::remove_file(p);
        }
    }

    #[test]
    fn lossless_jpeg_png_and_webp_metadata_strip_preserves_image_payload() {
        // Synthetic JPEG with APP0 (keep), APP1 EXIF (strip), APP2 ICC (keep), COM (strip), SOS scan (keep)
        let jpg_in = temp_path("in.jpg");
        let jpg_out = temp_path("out.jpg");
        let mut jpg = Vec::new();
        jpg.extend_from_slice(&[0xFF, 0xD8]); // SOI
        jpg.extend_from_slice(&[0xFF, 0xE0, 0x00, 0x07, b'J', b'F', b'I', b'F', 0x00]); // APP0
        jpg.extend_from_slice(&[0xFF, 0xE1, 0x00, 0x08, b'E', b'x', b'i', b'f', 0x00, 0x00]); // APP1
        let icc = b"ICC_PROFILE\0data";
        jpg.extend_from_slice(&[0xFF, 0xE2]);
        jpg.extend_from_slice(&((icc.len() + 2) as u16).to_be_bytes());
        jpg.extend_from_slice(icc);
        jpg.extend_from_slice(&[0xFF, 0xFE, 0x00, 0x05, b'G', b'P', b'S']); // COM
        jpg.extend_from_slice(&[0xFF, 0xDA, 0x00, 0x04, 0x11, 0x22, 0x33, 0xFF, 0xD9]); // SOS + EOI
        fs::write(&jpg_in, &jpg).unwrap();

        let stripped = image_ops::strip_image_metadata_lossless(
            &jpg_in.to_string_lossy(),
            &jpg_out.to_string_lossy(),
        )
        .unwrap();
        assert_eq!(stripped, 2);
        let out_jpg = fs::read(&jpg_out).unwrap();
        assert!(!out_jpg.windows(4).any(|w| w == b"Exif"));
        assert!(!out_jpg.windows(3).any(|w| w == b"GPS"));
        assert!(out_jpg.windows(11).any(|w| w == b"ICC_PROFILE"));
        assert!(out_jpg.ends_with(&[0x11, 0x22, 0x33, 0xFF, 0xD9]));

        // Synthetic PNG with IHDR, eXIf (strip), tEXt (strip), IDAT (keep), IEND
        let png_in = temp_path("in.png");
        let png_out = temp_path("out.png");
        let mut png = b"\x89PNG\r\n\x1a\n".to_vec();
        let push_chunk = |buf: &mut Vec<u8>, ty: &[u8; 4], data: &[u8]| {
            buf.extend_from_slice(&(data.len() as u32).to_be_bytes());
            buf.extend_from_slice(ty);
            buf.extend_from_slice(data);
            buf.extend_from_slice(&[0, 0, 0, 0]);
        };
        push_chunk(&mut png, b"IHDR", &[0; 13]);
        push_chunk(&mut png, b"eXIf", b"GPS_COORDS");
        push_chunk(&mut png, b"tEXt", b"Author\0Secret");
        push_chunk(&mut png, b"IDAT", b"PIXEL_BYTES");
        push_chunk(&mut png, b"IEND", &[]);
        fs::write(&png_in, &png).unwrap();

        let png_stripped = image_ops::strip_image_metadata_lossless(
            &png_in.to_string_lossy(),
            &png_out.to_string_lossy(),
        )
        .unwrap();
        assert_eq!(png_stripped, 2);
        let out_png = fs::read(&png_out).unwrap();
        assert!(!out_png.windows(4).any(|w| w == b"eXIf"));
        assert!(!out_png.windows(4).any(|w| w == b"tEXt"));
        assert!(out_png.windows(11).any(|w| w == b"PIXEL_BYTES"));

        // Synthetic WebP with VP8X (EXIF+XMP flags set), VP8, EXIF (strip), XMP (strip)
        let webp_in = temp_path("in.webp");
        let webp_out = temp_path("out.webp");
        let mut webp_body = Vec::new();
        let push_riff = |buf: &mut Vec<u8>, fourcc: &[u8; 4], data: &[u8]| {
            buf.extend_from_slice(fourcc);
            buf.extend_from_slice(&(data.len() as u32).to_le_bytes());
            buf.extend_from_slice(data);
            if data.len() & 1 == 1 {
                buf.push(0);
            }
        };
        push_riff(&mut webp_body, b"VP8X", &[0x0C, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
        push_riff(&mut webp_body, b"VP8 ", b"FRAME");
        push_riff(&mut webp_body, b"EXIF", b"CAMERA");
        push_riff(&mut webp_body, b"XMP ", b"XMPDATA");
        let mut webp = b"RIFF".to_vec();
        webp.extend_from_slice(&((4 + webp_body.len()) as u32).to_le_bytes());
        webp.extend_from_slice(b"WEBP");
        webp.extend_from_slice(&webp_body);
        fs::write(&webp_in, &webp).unwrap();

        let webp_stripped = image_ops::strip_image_metadata_lossless(
            &webp_in.to_string_lossy(),
            &webp_out.to_string_lossy(),
        )
        .unwrap();
        assert_eq!(webp_stripped, 2);
        let out_webp = fs::read(&webp_out).unwrap();
        assert!(!out_webp.windows(4).any(|w| w == b"EXIF"));
        assert!(!out_webp.windows(4).any(|w| w == b"XMP "));
        assert_eq!(out_webp[20] & 0x0C, 0); // EXIF + XMP flags cleared in VP8X

        for p in [jpg_in, jpg_out, png_in, png_out, webp_in, webp_out] {
            let _ = fs::remove_file(p);
        }
    }

    #[test]
    fn remove_background_strips_outer_backdrop_and_keeps_enclosed_white_interior() {
        let w = 16usize;
        let h = 16usize;
        let white = 0xFFFF_FFFFu32 as i32;
        let dark = 0xFF14_1414u32 as i32;
        let mut pixels = vec![white; w * h];

        // Draw a closed dark ring from (4..=11, 4..=11) with a white interior (6..=9, 6..=9)
        for y in 4..=11 {
            for x in 4..=11 {
                if x < 6 || x > 9 || y < 6 || y > 9 {
                    pixels[y * w + x] = dark;
                }
            }
        }

        let transparent = image_ops::remove_background_rgba(&mut pixels, w, h, 42);
        assert!(transparent > 0);
        // Outer corner is stripped
        assert_eq!((pixels[0] as u32 >> 24) & 0xFF, 0);
        // Enclosed white center stays opaque
        assert_eq!((pixels[8 * w + 8] as u32 >> 24) & 0xFF, 255);
        // Ring stays opaque
        assert_eq!((pixels[5 * w + 5] as u32 >> 24) & 0xFF, 255);
    }
}
