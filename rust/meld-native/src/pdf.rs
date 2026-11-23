use lopdf::{Dictionary, Document, Object, ObjectId};
use std::collections::BTreeMap;

/// Collects inheritable page attributes (/MediaBox, /CropBox, /Resources, /Rotate)
/// by walking up the /Parent chain in the source PDF so detached pages remain self-contained.
fn resolve_inherited_attr(doc: &Document, page_id: ObjectId, key: &[u8]) -> Option<Object> {
    let mut current_id = Some(page_id);
    let mut depth = 0;
    while let Some(id) = current_id {
        if depth > 32 {
            break;
        }
        depth += 1;
        let Ok(Object::Dictionary(dict)) = doc.get_object(id) else {
            break;
        };
        if let Ok(val) = dict.get(key) {
            return Some(val.clone());
        }
        current_id = dict.get(b"Parent").ok().and_then(|o| o.as_reference().ok());
    }
    None
}

/// Losslessly merges 2+ PDF files into a single vector-preserving PDF document.
/// Keeps original fonts, text streams, vector graphics, and page dimensions intact.
pub fn merge_pdfs(input_paths: &[String], output_path: &str) -> Result<usize, String> {
    if input_paths.len() < 2 {
        return Err("At least 2 PDF files are required to merge".to_string());
    }

    let mut max_id: u32 = 1;
    let mut ordered_page_ids: Vec<ObjectId> = Vec::new();
    let mut merged_objects: BTreeMap<ObjectId, Object> = BTreeMap::new();

    for path in input_paths {
        let mut doc = Document::load(path)
            .map_err(|e| format!("Failed to load PDF '{}': {}", path, e))?;
        if doc.is_encrypted() {
            return Err(format!("Encrypted PDF '{}' requires raster fallback", path));
        }

        // Materialize inherited page attributes before detaching pages from their old /Pages tree.
        let page_ids_before: Vec<ObjectId> = doc.get_pages().into_values().collect();
        if page_ids_before.is_empty() {
            return Err(format!("PDF '{}' contains zero pages", path));
        }

        for page_id in &page_ids_before {
            let inherited: Vec<(&[u8], Object)> = [
                b"MediaBox".as_slice(),
                b"CropBox".as_slice(),
                b"Resources".as_slice(),
                b"Rotate".as_slice(),
            ]
            .into_iter()
            .filter_map(|key| resolve_inherited_attr(&doc, *page_id, key).map(|v| (key, v)))
            .collect();

            if let Ok(Object::Dictionary(dict)) = doc.get_object_mut(*page_id) {
                for (key, val) in inherited {
                    if !dict.has(key) {
                        dict.set(key, val);
                    }
                }
            }
        }

        doc.renumber_objects_with(max_id);
        max_id = doc.max_id.saturating_add(1);

        let page_ids: Vec<ObjectId> = doc.get_pages().into_values().collect();
        ordered_page_ids.extend(page_ids);

        for (obj_id, obj) in doc.objects {
            let skip = match &obj {
                Object::Dictionary(d) => matches!(
                    d.get(b"Type").and_then(|t| t.as_name()),
                    Ok(b"Catalog") | Ok(b"Pages")
                ),
                _ => false,
            };
            if !skip {
                merged_objects.insert(obj_id, obj);
            }
        }
    }

    if ordered_page_ids.is_empty() {
        return Err("Merged PDF contains zero pages".to_string());
    }

    let mut out_doc = Document::with_version("1.5");
    out_doc.objects = merged_objects;
    out_doc.max_id = max_id;

    let pages_id = out_doc.new_object_id();
    let catalog_id = out_doc.new_object_id();

    for &page_id in &ordered_page_ids {
        if let Ok(Object::Dictionary(dict)) = out_doc.get_object_mut(page_id) {
            dict.set("Parent", Object::Reference(pages_id));
        }
    }

    let kids: Vec<Object> = ordered_page_ids
        .iter()
        .map(|&id| Object::Reference(id))
        .collect();
    let page_count = kids.len();

    let mut pages_dict = Dictionary::new();
    pages_dict.set("Type", Object::Name(b"Pages".to_vec()));
    pages_dict.set("Kids", Object::Array(kids));
    pages_dict.set("Count", Object::Integer(page_count as i64));
    out_doc.objects.insert(pages_id, Object::Dictionary(pages_dict));

    let mut catalog_dict = Dictionary::new();
    catalog_dict.set("Type", Object::Name(b"Catalog".to_vec()));
    catalog_dict.set("Pages", Object::Reference(pages_id));
    out_doc.objects.insert(catalog_id, Object::Dictionary(catalog_dict));

    out_doc.trailer.set("Root", Object::Reference(catalog_id));
    out_doc.prune_objects();
    out_doc.renumber_objects();
    out_doc.compress();
    out_doc
        .save(output_path)
        .map_err(|e| format!("Failed to save merged PDF: {}", e))?;

    Ok(page_count)
}

/// Losslessly extracts an inclusive 1-based page range into a standalone vector PDF.
pub fn split_pdf(
    input_path: &str,
    start_1based: u32,
    end_1based: u32,
    output_path: &str,
) -> Result<usize, String> {
    let mut doc = Document::load(input_path)
        .map_err(|e| format!("Failed to load PDF '{}': {}", input_path, e))?;
    if doc.is_encrypted() {
        return Err("Encrypted PDF requires raster fallback".to_string());
    }

    let total = doc.get_pages().len() as u32;
    if total == 0 {
        return Err("PDF contains zero pages".to_string());
    }

    let start = start_1based.clamp(1, total);
    let end = end_1based.clamp(start, total);
    let to_delete: Vec<u32> = (1..=total).filter(|p| *p < start || *p > end).collect();

    if !to_delete.is_empty() {
        doc.delete_pages(&to_delete);
    }

    doc.prune_objects();
    doc.renumber_objects();
    doc.save(output_path)
        .map_err(|e| format!("Failed to save split PDF: {}", e))?;

    Ok((end - start + 1) as usize)
}

/// Losslessly strips /Info metadata, /ID tracking identifiers, and XMP /Metadata streams
/// without rasterizing page vectors or text.
pub fn sanitize_pdf(input_path: &str, output_path: &str) -> Result<usize, String> {
    let mut doc = Document::load(input_path)
        .map_err(|e| format!("Failed to load PDF '{}': {}", input_path, e))?;
    if doc.is_encrypted() {
        return Err("Encrypted PDF requires raster fallback".to_string());
    }

    let page_count = doc.get_pages().len();
    if page_count == 0 {
        return Err("PDF contains zero pages".to_string());
    }

    if let Some(Object::Reference(info_id)) = doc.trailer.remove(b"Info") {
        doc.objects.remove(&info_id);
    }
    doc.trailer.remove(b"ID");

    let mut metadata_obj_ids: Vec<ObjectId> = Vec::new();
    for (&obj_id, obj) in doc.objects.iter_mut() {
        match obj {
            Object::Dictionary(dict) => {
                if let Some(Object::Reference(meta_id)) = dict.remove(b"Metadata") {
                    metadata_obj_ids.push(meta_id);
                }
                if matches!(
                    dict.get(b"Type").and_then(|t| t.as_name()),
                    Ok(b"Metadata")
                ) {
                    metadata_obj_ids.push(obj_id);
                }
            }
            Object::Stream(stream) => {
                if let Some(Object::Reference(meta_id)) = stream.dict.remove(b"Metadata") {
                    metadata_obj_ids.push(meta_id);
                }
                if matches!(
                    stream.dict.get(b"Type").and_then(|t| t.as_name()),
                    Ok(b"Metadata")
                ) {
                    metadata_obj_ids.push(obj_id);
                }
            }
            _ => {}
        }
    }

    for meta_id in metadata_obj_ids {
        doc.objects.remove(&meta_id);
    }

    doc.prune_objects();
    doc.renumber_objects();
    doc.save(output_path)
        .map_err(|e| format!("Failed to save sanitized PDF: {}", e))?;

    Ok(page_count)
}
