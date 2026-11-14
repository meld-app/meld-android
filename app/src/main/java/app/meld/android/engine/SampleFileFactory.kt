package app.meld.android.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object SampleFileFactory {

    /**
     * Generates a 4-page multi-slide PDF in cacheDir for immediate one-tap testing
     * of LOCK/STAMP, HANDOUT (2-up/4-up), SPLIT PDF, and REDACT.
     */
    fun createSamplePdf(dir: File): File {
        val outFile = File(dir, "MELD_Lecture_Deck_4Pages.pdf")
        val doc = PdfDocument()

        val titles = listOf(
            "SLIDE 01 • SYSTEMS ARCHITECTURE",
            "SLIDE 02 • ZERO-CLOUD LOCAL PIPELINE",
            "SLIDE 03 • BINARY-SEARCH COMPRESSION",
            "SLIDE 04 • STUDENT HANDOUT & REDACTION"
        )

        val bgPaint = Paint().apply { color = Color.WHITE }
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 24, 33)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 52, 68)
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val idPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 40, 40)
            textSize = 12f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        for ((idx, slideTitle) in titles.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, idx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)
            canvas.drawRoundRect(RectF(36f, 40f, 559f, 150f), 16f, 16f, bannerPaint)
            canvas.drawRoundRect(RectF(36f, 40f, 52f, 150f), 8f, 8f, accentPaint)
            canvas.drawText(slideTitle, 72f, 102f, titlePaint)

            // Simulated sensitive ID zone at top-right (targeted by REDACT)
            canvas.drawText("DOC-ID: MELD-9940-${idx + 101}", 345f, 118f, idPaint)

            // Diagram cards
            for (card in 0..2) {
                val top = 195f + card * 165f
                val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(228, 232, 240)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(RectF(44f, top, 551f, top + 135f), 14f, 14f, cardBorder)
                canvas.drawText(
                    "Module ${idx + 1}.${card + 1} — Hardware-Accelerated Native Execution",
                    64f,
                    top + 42f,
                    bodyPaint
                )
                canvas.drawText(
                    "• 100% On-device PDF, Image, Audio & Table processing with zero telemetry.",
                    64f,
                    top + 76f,
                    bodyPaint
                )
                canvas.drawText(
                    "• Built with Native Kotlin + Jetpack Compose for Android.",
                    64f,
                    top + 104f,
                    bodyPaint
                )
            }

            // Footer zone (also covered by redaction test)
            canvas.drawText(
                "CONFIDENTIAL TOKEN: USR-KEY-8821-MELD • PAGE ${idx + 1} OF 4",
                80f,
                680f,
                idPaint
            )

            doc.finishPage(page)
        }

        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * Generates a high-resolution studio subject image on a clean chroma background
     * for testing REMOVE BG and COMPRESS.
     */
    fun createSampleImage(dir: File): File {
        val outFile = File(dir, "MELD_Studio_Subject.png")
        val width = 1080
        val height = 1080
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Uniform studio backdrop (easily isolated by REMOVE BG)
        canvas.drawColor(Color.rgb(26, 32, 46))

        // Foreground vibrant geometric emblem in the center
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43)
            style = Paint.Style.FILL
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 179, 71)
            style = Paint.Style.FILL
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 24, 33)
            textSize = 54f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawRoundRect(RectF(220f, 220f, 860f, 860f), 140f, 140f, outerPaint)
        canvas.drawCircle(540f, 540f, 230f, innerPaint)
        canvas.drawRoundRect(RectF(390f, 465f, 690f, 615f), 36f, 36f, corePaint)
        canvas.drawText("MELD", 540f, 560f, labelPaint)

        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return outFile
    }

    /**
     * Generates a synthetic 16-bit PCM WAV file for testing the VISUALIZE waveform tool.
     */
    fun createSampleAudioWav(dir: File): File {
        val outFile = File(dir, "MELD_Studio_Synth.wav")
        val sampleRate = 22050
        val numSamples = sampleRate * 2 // 2 seconds
        val dataSize = numSamples * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF WAVE header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // Mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = (0.35 + 0.65 * kotlin.math.abs(sin(t * 4.5)))
            val wave = sin(2.0 * Math.PI * 220.0 * t) * 0.6 + sin(2.0 * Math.PI * 440.0 * t) * 0.4
            val sample = (wave * envelope * 28000).toInt().coerceIn(-32767, 32767).toShort()
            buffer.putShort(sample)
        }

        FileOutputStream(outFile).use { it.write(buffer.array()) }
        return outFile
    }

    /**
     * Generates a structured CSV spreadsheet for testing TABLE -> JSON conversion.
     */
    fun createSampleCsv(dir: File): File {
        val outFile = File(dir, "MELD_Benchmark_Report.csv")
        val csv = """
            Tool,Category,Platform,Offline,AvgSpeedMs
            REMOVE BG,Image Studio,Android + macOS,true,145
            COMPRESS,Image Studio,Android + macOS,true,92
            LOCK / STAMP,PDF & Docs,Android + macOS,true,180
            HANDOUT 4-UP,PDF & Docs,Android + macOS,true,210
            VISUALIZE,Audio & Video,Android + macOS,true,115
        """.trimIndent()
        outFile.writeText(csv)
        return outFile
    }

    /**
     * Generates a valid 3-slide 16:9 OOXML `.pptx` sample presentation with theme scheme fonts/colors,
     * master inheritance, grouped badge shapes, rich styled text runs, an embedded PNG image, a table,
     * metadata properties, and speaker notes (pure JVM — no Android framework required).
     */
    fun createSamplePptx(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, "MELD_Deck_3Slides.pptx")

        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Default Extension="png" ContentType="image/png"/>
              <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
              <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
              <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
              <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
              <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
              <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
              <Override PartName="/ppt/slides/slide3.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
              <Override PartName="/ppt/notesSlides/notesSlide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"/>
              <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
              <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
            </Types>
        """.trimIndent()

        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
            </Relationships>
        """.trimIndent()

        val coreXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <dc:title>MELD Offline Architecture Deck</dc:title>
              <dc:creator>MELD Studio Author</dc:creator>
              <cp:lastModifiedBy>MELD Review Team</cp:lastModifiedBy>
              <cp:revision>4</cp:revision>
              <dcterms:created xsi:type="dcterms:W3CDTF">2026-11-14T10:00:00Z</dcterms:created>
              <dcterms:modified xsi:type="dcterms:W3CDTF">2026-11-14T10:30:00Z</dcterms:modified>
            </cp:coreProperties>
        """.trimIndent()

        val appXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
              <Application>MELD Sample Generator</Application>
              <Company>MELD Private Workspace</Company>
              <Manager>Architecture Lead</Manager>
              <Slides>3</Slides>
            </Properties>
        """.trimIndent()

        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
              <p:sldIdLst>
                <p:sldId id="256" r:id="rId2"/>
                <p:sldId id="257" r:id="rId3"/>
                <p:sldId id="258" r:id="rId4"/>
              </p:sldIdLst>
              <p:sldSz cx="12192000" cy="6858000" type="screen16x9"/>
              <p:notesSz cx="6858000" cy="9144000"/>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
              <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide3.xml"/>
              <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
            </Relationships>
        """.trimIndent()

        val themeXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="MeldNeoBrutalist">
              <a:themeElements>
                <a:clrScheme name="MeldColors">
                  <a:dk1><a:srgbClr val="1A1A1A"/></a:dk1>
                  <a:lt1><a:srgbClr val="F5F3EE"/></a:lt1>
                  <a:dk2><a:srgbClr val="2B2A27"/></a:dk2>
                  <a:lt2><a:srgbClr val="ECE9E1"/></a:lt2>
                  <a:accent1><a:srgbClr val="E85D04"/></a:accent1>
                  <a:accent2><a:srgbClr val="1D4ED8"/></a:accent2>
                  <a:accent3><a:srgbClr val="15803D"/></a:accent3>
                  <a:accent4><a:srgbClr val="B45309"/></a:accent4>
                  <a:accent5><a:srgbClr val="7C3AED"/></a:accent5>
                  <a:accent6><a:srgbClr val="3F6212"/></a:accent6>
                  <a:hlink><a:srgbClr val="1D4ED8"/></a:hlink>
                  <a:folHlink><a:srgbClr val="6D28D9"/></a:folHlink>
                </a:clrScheme>
                <a:fontScheme name="MeldFonts">
                  <a:majorFont><a:latin typeface="Georgia"/></a:majorFont>
                  <a:minorFont><a:latin typeface="Helvetica Neue"/></a:minorFont>
                </a:fontScheme>
                <a:fmtScheme name="Default"/>
              </a:themeElements>
            </a:theme>
        """.trimIndent()

        val slideMasterXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                  <p:grpSpPr/>
                  <p:sp>
                    <p:nvSpPr><p:cNvPr id="99" name="FooterBadge"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                    <p:spPr>
                      <a:xfrm><a:off x="8382000" y="6299200"/><a:ext cx="3200400" cy="304800"/></a:xfrm>
                      <a:prstGeom prst="rect"/>
                      <a:noFill/>
                    </p:spPr>
                    <p:txBody>
                      <a:bodyPr anchor="ctr"/>
                      <a:p>
                        <a:pPr algn="r"/>
                        <a:r><a:rPr lang="en-US" sz="1000" b="1"><a:solidFill><a:srgbClr val="6B6964"/></a:solidFill><a:latin typeface="+mn-lt"/></a:rPr><a:t>MELD 100% OFFLINE DECK</a:t></a:r>
                      </a:p>
                    </p:txBody>
                  </p:sp>
                </p:spTree>
              </p:cSld>
              <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
              <p:txStyles>
                <p:titleStyle>
                  <a:lvl1pPr><a:defRPr sz="3200" b="1"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="+mj-lt"/></a:defRPr></a:lvl1pPr>
                </p:titleStyle>
                <p:bodyStyle>
                  <a:lvl1pPr marL="228600" indent="-228600"><a:defRPr sz="1800"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="+mn-lt"/></a:defRPr></a:lvl1pPr>
                </p:bodyStyle>
              </p:txStyles>
            </p:sldMaster>
        """.trimIndent()

        val slideMasterRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
            </Relationships>
        """.trimIndent()

        val slideLayoutXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="titleAndBody">
              <p:cSld>
                <p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                  <p:grpSpPr/>
                  <p:sp>
                    <p:nvSpPr><p:cNvPr id="2" name="LayoutTitle"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:spPr><a:xfrm><a:off x="685800" y="457200"/><a:ext cx="10820400" cy="914400"/></a:xfrm><a:prstGeom prst="rect"/></p:spPr>
                    <p:txBody><a:bodyPr anchor="ctr"/><a:p/></p:txBody>
                  </p:sp>
                </p:spTree>
              </p:cSld>
            </p:sldLayout>
        """.trimIndent()

        val slideLayoutRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld>
                <p:bg><p:bgPr><a:solidFill><a:schemeClr val="lt1"/></a:solidFill></p:bgPr></p:bg>
                <p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                  <p:grpSpPr/>
                  <p:grpSp>
                    <p:nvGrpSpPr><p:cNvPr id="10" name="HeroBadge"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr>
                      <a:xfrm>
                        <a:off x="762000" y="914400"/><a:ext cx="2286000" cy="609600"/>
                        <a:chOff x="0" y="0"/><a:chExt cx="2286000" cy="609600"/>
                      </a:xfrm>
                    </p:grpSpPr>
                    <p:sp>
                      <p:nvSpPr><p:cNvPr id="11" name="Pill"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                      <p:spPr>
                        <a:xfrm><a:off x="0" y="0"/><a:ext cx="2286000" cy="609600"/></a:xfrm>
                        <a:prstGeom prst="roundRect"><a:avLst><a:gd name="adj" fmla="val 50000"/></a:avLst></a:prstGeom>
                        <a:solidFill><a:schemeClr val="accent1"/></a:solidFill>
                        <a:ln w="25400"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill></a:ln>
                      </p:spPr>
                      <p:txBody>
                        <a:bodyPr anchor="ctr"/>
                        <a:p><a:pPr algn="ctr"/><a:r><a:rPr lang="en-US" sz="1400" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:latin typeface="+mn-lt"/></a:rPr><a:t>SLIDE STUDIO</a:t></a:r></a:p>
                      </p:txBody>
                    </p:sp>
                  </p:grpSp>
                  <p:sp>
                    <p:nvSpPr><p:cNvPr id="2" name="CoverTitle"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                    <p:spPr><a:xfrm><a:off x="762000" y="1828800"/><a:ext cx="10668000" cy="1828800"/></a:xfrm><a:prstGeom prst="rect"/><a:noFill/></p:spPr>
                    <p:txBody>
                      <a:bodyPr anchor="t"/>
                      <a:p><a:r><a:rPr lang="en-US" sz="4400" b="1"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="+mj-lt"/></a:rPr><a:t>MELD Offline Slide Engine</a:t></a:r></a:p>
                      <a:p><a:pPr><a:spcBef><a:spcPts val="1400"/></a:spcBef></a:pPr><a:r><a:rPr lang="en-US" sz="2000"><a:solidFill><a:srgbClr val="4A4843"/></a:solidFill><a:latin typeface="+mn-lt"/></a:rPr><a:t>Zero-cloud OOXML parsing, exact 16:9 geometry &amp; font preservation</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                  <p:sp>
                    <p:nvSpPr><p:cNvPr id="3" name="RuleLine"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                    <p:spPr>
                      <a:xfrm><a:off x="762000" y="4267200"/><a:ext cx="4572000" cy="0"/></a:xfrm>
                      <a:prstGeom prst="line"/>
                      <a:ln w="38100"><a:solidFill><a:schemeClr val="accent1"/></a:solidFill></a:ln>
                    </p:spPr>
                  </p:sp>
                </p:spTree>
              </p:cSld>
            </p:sld>
        """.trimIndent()

        val slide1Rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide1.xml"/>
            </Relationships>
        """.trimIndent()

        val notesSlide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld><p:spTree>
                <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                <p:grpSpPr/>
                <p:sp>
                  <p:nvSpPr><p:cNvPr id="2" name="Notes"/><p:cNvSpPr/><p:nvPr><p:ph type="body"/></p:nvPr></p:nvSpPr>
                  <p:spPr/>
                  <p:txBody>
                    <a:bodyPr/>
                    <a:p><a:r><a:t>Emphasize 100% local execution and byte-preserving sanitization before client sharing.</a:t></a:r></a:p>
                  </p:txBody>
                </p:sp>
              </p:spTree></p:cSld>
            </p:notes>
        """.trimIndent()

        val slide2Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld>
                <p:bg><p:bgPr><a:solidFill><a:schemeClr val="lt1"/></a:solidFill></p:bgPr></p:bg>
                <p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                  <p:grpSpPr/>
                  <p:sp>
                    <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:spPr><a:prstGeom prst="rect"/></p:spPr>
                    <p:txBody>
                      <a:bodyPr anchor="ctr"/>
                      <a:p><a:r><a:rPr lang="en-US" sz="3200" b="1"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="+mj-lt"/></a:rPr><a:t>Architecture &amp; Format Fidelity</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                  <p:sp>
                    <p:nvSpPr><p:cNvPr id="3" name="FeatureCard"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                    <p:spPr>
                      <a:xfrm><a:off x="685800" y="1600200"/><a:ext cx="6858000" cy="4114800"/></a:xfrm>
                      <a:prstGeom prst="roundRect"><a:avLst><a:gd name="adj" fmla="val 6000"/></a:avLst></a:prstGeom>
                      <a:solidFill><a:schemeClr val="lt2"/></a:solidFill>
                      <a:ln w="25400"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill></a:ln>
                    </p:spPr>
                    <p:txBody>
                      <a:bodyPr lIns="274320" tIns="228600" rIns="228600" bIns="228600"/>
                      <a:p>
                        <a:pPr lvl="0" marL="274320" indent="-182880"><a:spcAft><a:spcPts val="1200"/></a:spcAft><a:buChar char="•"/></a:pPr>
                        <a:r><a:rPr lang="en-US" sz="1800" b="1"><a:solidFill><a:schemeClr val="accent1"/></a:solidFill><a:latin typeface="+mn-lt"/></a:rPr><a:t>Read-Only Source: </a:t></a:r>
                        <a:r><a:rPr lang="en-US" sz="1800"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="+mn-lt"/></a:rPr><a:t>Input .pptx structure stays untouched.</a:t></a:r>
                      </a:p>
                      <a:p>
                        <a:pPr lvl="0" marL="274320" indent="-182880"><a:spcAft><a:spcPts val="1200"/></a:spcAft><a:buChar char="•"/></a:pPr>
                        <a:r><a:rPr lang="en-US" sz="1800" i="1"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="Georgia"/></a:rPr><a:t>Font Pipeline: </a:t></a:r>
                        <a:r><a:rPr lang="en-US" sz="1800"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="+mn-lt"/></a:rPr><a:t>Embedded .odttf/.ttf + crisp family mapping.</a:t></a:r>
                      </a:p>
                      <a:p>
                        <a:pPr lvl="0" marL="274320" indent="-182880"><a:buChar char="•"/></a:pPr>
                        <a:r><a:rPr lang="en-US" sz="1800"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="+mn-lt"/></a:rPr><a:t>Exact Aspect Ratio: 960 x 540 pt canvas per slide</a:t></a:r>
                        <a:r><a:rPr lang="en-US" sz="1800" baseline="30000"><a:solidFill><a:schemeClr val="accent2"/></a:solidFill><a:latin typeface="+mn-lt"/></a:rPr><a:t>2</a:t></a:r>
                      </a:p>
                    </p:txBody>
                  </p:sp>
                  <p:pic>
                    <p:nvPicPr><p:cNvPr id="4" name="SampleGraphic"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
                    <p:blipFill><a:blip r:embed="rId2"/></p:blipFill>
                    <p:spPr><a:xfrm><a:off x="8001000" y="1828800"/><a:ext cx="3352800" cy="3352800"/></a:xfrm><a:prstGeom prst="rect"/></p:spPr>
                  </p:pic>
                </p:spTree>
              </p:cSld>
            </p:sld>
        """.trimIndent()

        val slide2Rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>
            </Relationships>
        """.trimIndent()

        val slide3Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld>
                <p:bg><p:bgPr><a:solidFill><a:schemeClr val="lt1"/></a:solidFill></p:bgPr></p:bg>
                <p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                  <p:grpSpPr/>
                  <p:sp>
                    <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:spPr><a:prstGeom prst="rect"/></p:spPr>
                    <p:txBody>
                      <a:bodyPr anchor="ctr"/>
                      <a:p><a:r><a:rPr lang="en-US" sz="3200" b="1"><a:solidFill><a:schemeClr val="dk1"/></a:solidFill><a:latin typeface="+mj-lt"/></a:rPr><a:t>Conversion Matrix &amp; Benchmarks</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                  <p:graphicFrame>
                    <p:nvGraphicFramePr><p:cNvPr id="3" name="MatrixTable"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>
                    <p:xfrm><a:off x="685800" y="1676400"/><a:ext cx="10820400" cy="3657600"/></p:xfrm>
                    <a:graphic>
                      <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">
                        <a:tbl>
                          <a:tblGrid>
                            <a:gridCol w="3606800"/><a:gridCol w="4368800"/><a:gridCol w="2844800"/>
                          </a:tblGrid>
                          <a:tr h="762000">
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1600" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Tool</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:schemeClr val="dk1"/></a:solidFill></a:tcPr></a:tc>
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1600" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Guarantee</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:schemeClr val="dk1"/></a:solidFill></a:tcPr></a:tc>
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1600" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Target</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:schemeClr val="dk1"/></a:solidFill></a:tcPr></a:tc>
                          </a:tr>
                          <a:tr h="762000">
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500" b="1"/><a:t>PPTX -&gt; PDF</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500"/><a:t>Exact slide dimensions &amp; fonts</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500"/><a:t>PDF (960x540)</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
                          </a:tr>
                          <a:tr h="762000">
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500" b="1"/><a:t>PPTX -&gt; MD</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:schemeClr val="lt2"/></a:solidFill></a:tcPr></a:tc>
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500"/><a:t>Headings, bullets, GFM tables, notes</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:schemeClr val="lt2"/></a:solidFill></a:tcPr></a:tc>
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500"/><a:t>Markdown (.md)</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:schemeClr val="lt2"/></a:solidFill></a:tcPr></a:tc>
                          </a:tr>
                          <a:tr h="762000">
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500" b="1"/><a:t>Sanitize</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500"/><a:t>Byte-identical slides, zero PII</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
                            <a:tc><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:rPr sz="1500"/><a:t>Clean .pptx</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
                          </a:tr>
                        </a:tbl>
                      </a:graphicData>
                    </a:graphic>
                  </p:graphicFrame>
                </p:spTree>
              </p:cSld>
            </p:sld>
        """.trimIndent()

        val slide3Rels = slideLayoutRels.replace("../slideMasters/slideMaster1.xml", "../slideLayouts/slideLayout1.xml")
            .replace("relationships/slideMaster", "relationships/slideLayout")

        val samplePng = encodeMinimalSamplePng(64, 64)

        val entries: List<Pair<String, ByteArray>> = listOf(
            "[Content_Types].xml" to contentTypes.toByteArray(Charsets.UTF_8),
            "_rels/.rels" to rootRels.toByteArray(Charsets.UTF_8),
            "docProps/core.xml" to coreXml.toByteArray(Charsets.UTF_8),
            "docProps/app.xml" to appXml.toByteArray(Charsets.UTF_8),
            "ppt/presentation.xml" to presentationXml.toByteArray(Charsets.UTF_8),
            "ppt/_rels/presentation.xml.rels" to presentationRels.toByteArray(Charsets.UTF_8),
            "ppt/theme/theme1.xml" to themeXml.toByteArray(Charsets.UTF_8),
            "ppt/slideMasters/slideMaster1.xml" to slideMasterXml.toByteArray(Charsets.UTF_8),
            "ppt/slideMasters/_rels/slideMaster1.xml.rels" to slideMasterRels.toByteArray(Charsets.UTF_8),
            "ppt/slideLayouts/slideLayout1.xml" to slideLayoutXml.toByteArray(Charsets.UTF_8),
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to slideLayoutRels.toByteArray(Charsets.UTF_8),
            "ppt/slides/slide1.xml" to slide1Xml.toByteArray(Charsets.UTF_8),
            "ppt/slides/_rels/slide1.xml.rels" to slide1Rels.toByteArray(Charsets.UTF_8),
            "ppt/slides/slide2.xml" to slide2Xml.toByteArray(Charsets.UTF_8),
            "ppt/slides/_rels/slide2.xml.rels" to slide2Rels.toByteArray(Charsets.UTF_8),
            "ppt/slides/slide3.xml" to slide3Xml.toByteArray(Charsets.UTF_8),
            "ppt/slides/_rels/slide3.xml.rels" to slide3Rels.toByteArray(Charsets.UTF_8),
            "ppt/notesSlides/notesSlide1.xml" to notesSlide1Xml.toByteArray(Charsets.UTF_8),
            "ppt/media/image1.png" to samplePng,
        )

        java.util.zip.ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            for ((path, data) in entries) {
                zos.putNextEntry(java.util.zip.ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return outFile
    }

    private fun encodeMinimalSamplePng(width: Int, height: Int): ByteArray {
        val raw = ByteArray(height * (1 + width * 3))
        var idx = 0
        for (y in 0 until height) {
            raw[idx++] = 0 // Filter type None
            for (x in 0 until width) {
                val border = x < 3 || y < 3 || x >= width - 3 || y >= height - 3
                val diagonal = kotlin.math.abs(x - y) < 6
                when {
                    border -> {
                        raw[idx++] = 0x1A.toByte()
                        raw[idx++] = 0x1A.toByte()
                        raw[idx++] = 0x1A.toByte()
                    }
                    diagonal -> {
                        raw[idx++] = 0xE8.toByte()
                        raw[idx++] = 0x5D.toByte()
                        raw[idx++] = 0x04.toByte()
                    }
                    else -> {
                        raw[idx++] = (225 + (x * 20) / width).toByte()
                        raw[idx++] = (218 + (y * 22) / height).toByte()
                        raw[idx++] = 205.toByte()
                    }
                }
            }
        }

        val deflater = java.util.zip.Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val idatBaos = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            idatBaos.write(buf, 0, n)
        }
        deflater.end()

        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val ihdr = java.nio.ByteBuffer.allocate(13)
            .putInt(width)
            .putInt(height)
            .put(8) // bit depth
            .put(2) // color type RGB
            .put(0)
            .put(0)
            .put(0)
            .array()
        writePngChunk(out, "IHDR", ihdr)
        writePngChunk(out, "IDAT", idatBaos.toByteArray())
        writePngChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writePngChunk(out: java.io.ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = java.util.zip.CRC32().apply {
            update(typeBytes)
            update(data)
        }
        val lenBuf = java.nio.ByteBuffer.allocate(4).putInt(data.size).array()
        val crcBuf = java.nio.ByteBuffer.allocate(4).putInt(crc.value.toInt()).array()
        out.write(lenBuf)
        out.write(typeBytes)
        out.write(data)
        out.write(crcBuf)
    }
}

