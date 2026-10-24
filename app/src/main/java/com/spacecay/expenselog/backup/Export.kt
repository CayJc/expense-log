package com.spacecay.expenselog.backup

import android.content.Context
import android.net.Uri
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Range
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CSV and real .xlsx export of transactions. */
object Export {
    private val header = listOf("Date", "Time", "Type", "Category", "Subcategory", "Wallet", "To wallet", "Amount", "Currency",
        "Amount (main)", "Main currency", "Fee", "Payee", "Note", "Reference")

    private suspend fun rows(range: Range): List<List<Any>> {
        val d = Graph.dao
        val bookId = Graph.prefs.bookId.value
        val bd = BookData(bookId, d.getBook(bookId), d.getRates(bookId), d.getWallets(bookId), d.getCategories(bookId), emptyList())
        return d.getTxnsBetween(bookId, range.start, range.end).sortedBy { it.dateTime }.map { t ->
            val c = t.categoryId?.let { bd.catMap[it] }
            val parent = c?.parentId?.let { bd.catMap[it] }
            val signed = if (t.type == TxType.EXPENSE) -t.amount else t.amount
            listOf(
                Dates.fmt("yyyy-MM-dd", t.dateTime), Dates.fmt("HH:mm", t.dateTime),
                listOf("Expense", "Income", "Transfer")[t.type],
                (parent ?: c)?.name ?: if (t.debtId != null) "Debt" else if (t.goalId != null) "Goal" else "",
                if (parent != null) c?.name ?: "" else "",
                bd.walletMap[t.walletId]?.name ?: "", t.toWalletId?.let { bd.walletMap[it]?.name } ?: "",
                signed / 100.0, bd.currencyOf(t.walletId),
                (if (t.type == TxType.EXPENSE) -bd.mainAmount(t) else bd.mainAmount(t)) / 100.0, bd.main,
                t.fee / 100.0, t.payee, t.note, t.ref ?: "",
            )
        }
    }

    private fun csvCell(v: Any): String {
        val s = if (v is Double) "%.2f".format(java.util.Locale.US, v) else v.toString()
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    suspend fun csv(ctx: Context, uri: Uri, range: Range): Int {
        val rows = rows(range)
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM for Excel
            val w = out.bufferedWriter()
            w.write(header.joinToString(",") { csvCell(it) }); w.write("\r\n")
            rows.forEach { r -> w.write(r.joinToString(",") { csvCell(it) }); w.write("\r\n") }
            w.flush()
        }
        return rows.size
    }

    suspend fun xlsx(ctx: Context, uri: Uri, range: Range): Int {
        val rows = rows(range)
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { writeXlsx(it, listOf(header) + rows) }
        return rows.size
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun writeXlsx(out: OutputStream, data: List<List<Any>>) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
        }
        entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""")
        entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
        entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Transactions" sheetId="1" r:id="rId1"/></sheets></workbook>""")
        entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
        entry("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
</styleSheet>""")
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        sb.append("<cols><col min=\"1\" max=\"15\" width=\"16\" customWidth=\"1\"/></cols><sheetData>")
        data.forEachIndexed { ri, row ->
            sb.append("<row r=\"${ri + 1}\">")
            row.forEachIndexed { ci, v ->
                val ref = col(ci) + (ri + 1)
                when {
                    v is Double -> sb.append("<c r=\"$ref\" s=\"2\"><v>$v</v></c>")
                    else -> sb.append("<c r=\"$ref\" t=\"inlineStr\"${if (ri == 0) " s=\"1\"" else ""}><is><t>${esc(v.toString())}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        entry("xl/worksheets/sheet1.xml", sb.toString())
        zip.finish()
    }
}
