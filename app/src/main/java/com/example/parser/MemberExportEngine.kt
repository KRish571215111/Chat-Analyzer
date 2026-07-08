package com.example.parser

import android.content.Context
import android.net.Uri
import com.example.data.Participant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

object MemberExportEngine {
    
    suspend fun exportParticipants(
        context: Context,
        uri: Uri,
        format: String,
        participants: List<Participant>
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            val writer = OutputStreamWriter(outputStream)
            when (format.uppercase()) {
                "CSV" -> exportCsv(writer, participants)
                "VCF" -> exportVcf(writer, participants)
                "JSON" -> exportJson(writer, participants)
                "HTML" -> exportHtml(writer, participants)
                "TXT" -> exportTxt(writer, participants)
                else -> exportCsv(writer, participants)
            }
            writer.flush()
        }
    }

    private fun exportCsv(writer: OutputStreamWriter, participants: List<Participant>) {
        writer.write("Name,Phone,MessageCount\n")
        for (p in participants) {
            val safeName = if (p.name.contains(",")) "\"${p.name}\"" else p.name
            val phone = p.phone ?: ""
            writer.write("$safeName,$phone,${p.messageCount}\n")
        }
    }

    private fun exportVcf(writer: OutputStreamWriter, participants: List<Participant>) {
        for (p in participants) {
            writer.write("BEGIN:VCARD\n")
            writer.write("VERSION:3.0\n")
            writer.write("FN:${p.name}\n")
            if (p.phone != null) {
                writer.write("TEL;TYPE=CELL:${p.phone}\n")
            }
            writer.write("END:VCARD\n")
        }
    }

    private fun exportJson(writer: OutputStreamWriter, participants: List<Participant>) {
        val array = JSONArray()
        for (p in participants) {
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("phone", p.phone)
            obj.put("messageCount", p.messageCount)
            array.put(obj)
        }
        writer.write(array.toString(4))
    }

    private fun exportHtml(writer: OutputStreamWriter, participants: List<Participant>) {
        writer.write("<html><body><h1>Members</h1><table border='1'><tr><th>Name</th><th>Phone</th><th>Messages</th></tr>")
        for (p in participants) {
            val name = p.name.replace("<", "&lt;").replace(">", "&gt;")
            val phone = p.phone ?: ""
            writer.write("<tr><td>$name</td><td>$phone</td><td>${p.messageCount}</td></tr>")
        }
        writer.write("</table></body></html>")
    }

    private fun exportTxt(writer: OutputStreamWriter, participants: List<Participant>) {
        writer.write("Members List\n")
        writer.write("-------------------------\n")
        for (p in participants) {
            val phone = p.phone ?: "No phone"
            writer.write("${p.name} - $phone (${p.messageCount} msgs)\n")
        }
    }
}
