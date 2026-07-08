package com.example.parser

import android.content.Context
import android.net.Uri
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object MemberListGenerator {

    /**
     * Generates a suite of contacts files in different formats for validation:
     * - CSV (Standard name, phone columns, different headers, duplicates, mixed spacing)
     * - TXT (Only numbers, with mixed formatting, spaces, country codes, duplicates)
     * - TXT Names (Only names)
     * - JSON (Array of objects with names and phones)
     * - VCF (vCard format with FN and TEL fields)
     *
     * Each file represents roughly the same set of members with variations for testing.
     */
    fun generateSuite(context: Context): Map<String, Uri> {
        val cacheDir = context.cacheDir
        val suite = mutableMapOf<String, Uri>()

        // 1. Standard CSV with mixed headers, duplicate entries, whitespaces, different formatting
        val csvFile = File(cacheDir, "test_members_mixed.csv")
        csvFile.writeText(
            """
            Display Name, Contact Phone, Notes
            Alice Smith, +91 9876543210, Active admin
            Bob Johnson, +1 (555) 019-2834, Dev Lead
            Charlie Brown, 09876543210, Muted
            David Lee, 919876543210, Designer
            Emma Watson, 9876543210, Marketing
            Frank Harris, +44 7911 123456, Support
            Grace Hopper, +1-555-555-1212, Compiler developer
            Heisenberg, +55 11 99999-9999, Chemistry
            Isaac Newton, +33 1 42 27 78 78, Physics
            Jack Sparrow, +123456789, Captain
            Alice Smith, 9876543210, Duplicate entry with local phone format
            Duplicate Name, +91 9876543210, Duplicate phone with new name
            """.trimIndent()
        )
        suite["CSV"] = Uri.fromFile(csvFile)

        // 2. TXT with ONLY numbers (varying country codes, spacing, leading zero)
        val txtNumbersFile = File(cacheDir, "test_members_only_numbers.txt")
        txtNumbersFile.writeText(
            """
            +91 9876543210
            +1 (555) 019-2834
            09876543210
            919876543210
            9876543210
            +44 7911 123456
            +1-555-555-1212
            +55 11 99999-9999
            +33 1 42 27 78 78
            +123456789
            9876543210
            """.trimIndent()
        )
        suite["TXT_NUMBERS"] = Uri.fromFile(txtNumbersFile)

        // 3. TXT with ONLY names (for testing name-only imports)
        val txtNamesFile = File(cacheDir, "test_members_only_names.txt")
        txtNamesFile.writeText(
            """
            Alice Smith
            Bob Johnson
            Charlie Brown
            David Lee
            Emma Watson
            Frank Harris
            Grace Hopper
            Heisenberg
            Isaac Newton
            Jack Sparrow
            """.trimIndent()
        )
        suite["TXT_NAMES"] = Uri.fromFile(txtNamesFile)

        // 4. JSON format representing the members list
        val jsonFile = File(cacheDir, "test_members.json")
        val jsonArray = JSONArray()
        val rawData = listOf(
            Triple("Alice Smith", "+91 9876543210", "Active"),
            Triple("Bob Johnson", "+1 (555) 019-2834", "Dev"),
            Triple("Charlie Brown", "09876543210", "Muted"),
            Triple("David Lee", "919876543210", "Design"),
            Triple("Emma Watson", "9876543210", "Marketing"),
            Triple("Frank Harris", "+44 7911 123456", "Support"),
            Triple("Grace Hopper", "+1-555-555-1212", "Dev"),
            Triple("Heisenberg", "+55 11 99999-9999", "Chem"),
            Triple("Isaac Newton", "+33 1 42 27 78 78", "Math"),
            Triple("Jack Sparrow", "+123456789", "Cap")
        )
        for (item in rawData) {
            val obj = JSONObject()
            obj.put("display_name", item.first)
            obj.put("mobile", item.second)
            obj.put("role", item.third)
            jsonArray.put(obj)
        }
        jsonFile.writeText(jsonArray.toString(2))
        suite["JSON"] = Uri.fromFile(jsonFile)

        // 5. vCard (.vcf) format
        val vcfFile = File(cacheDir, "test_members.vcf")
        val vcfContent = StringBuilder()
        for (item in rawData) {
            vcfContent.append("BEGIN:VCARD\n")
            vcfContent.append("VERSION:3.0\n")
            vcfContent.append("FN:${item.first}\n")
            vcfContent.append("TEL;TYPE=CELL:${item.second}\n")
            vcfContent.append("END:VCARD\n")
        }
        vcfFile.writeText(vcfContent.toString())
        suite["VCF"] = Uri.fromFile(vcfFile)

        return suite
    }
}
