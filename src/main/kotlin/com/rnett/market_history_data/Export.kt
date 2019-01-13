package com.rnett.market_history_data

import com.google.gson.Gson
import com.rnett.core.get
import com.rnett.core.launchInAndJoinAll
import com.rnett.market_history_data.TypeData.Companion.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.cbor.CBOR
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.json.JSON
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

infix fun <K : Any, V : Any> KSerializer<K>.mapWith(vs: KSerializer<V>) = (this to vs).map

enum class ExportType{
    CSV, JSON, CBOR;
}

object ExportHistoryData{

    // [-bigfile file] [-typedir dir] [-datedir dir] [-localhost]
    @JvmStatic
    fun main(args: Array<String>) {
        export("-localhost" in args, args["-bigfile"], args["-typedir"], args["-datedir"],
            args["-formats"]?.split(",")?.map { ExportType.valueOf(it.toUpperCase()) } ?: listOf(ExportType.CSV))
    }

    fun export(useLocalhost: Boolean, bigFile: String?, typeFiles: String?, dateFiles: String?, formats: List<ExportType>){
        connectToDB(useLocalhost)

        if(listOfNotNull(bigFile, typeFiles, dateFiles).isEmpty())
            return


        formats.forEach {
            if (typeFiles != null){
                println("Exporting $it by type")
                exportAllSmallFilesByType(typeFiles, it)
            }

            if (dateFiles != null) {
                println("Exporting $it by date")
                exportAllSmallFilesByDate(dateFiles, it)
            }
        }

        println("Exporting big CBOR file")
        if(bigFile != null)
            exportLargeFile(bigFile)

    }

    fun exportLargeFile(file: String){
        val data = HistoryData.allByType()
        File(file).apply{
            createNewFile()
            writeText(CBOR.plain.dumps(IntSerializer mapWith HistoryData.serializer().list, data))
        }
    }

    fun exportAllSmallFilesByType(dir: String, exportType: ExportType) = runBlocking(context = Dispatchers.IO){

        val types = transaction{
            historydatas.run{
                slice(typeid).selectAll().distinct().map { it[typeid] }.toSet()
            }
        }

        types.launchInAndJoinAll(this){ type ->
            val file = dir.trim('/') + "/${exportType.name.toLowerCase()}/$type.${exportType.name.toLowerCase()}"
            transaction {
                val data = HistoryData[type]
                with(File(file)) {
                    this.parentFile.mkdirs()
                    createNewFile()

                    when(exportType){
                        ExportType.CSV -> {
                            writeText(HistoryData.csvHeader)
                            appendText("\n")

                            data.forEach { dat ->
                                appendText(dat.toCSV())
                                appendText("\n")
                            }
                        }
                        ExportType.JSON -> {
                            writeText(JSON.plain.stringify(HistoryData.serializer().list, data))
                        }
                        ExportType.CBOR -> {
                            writeText(CBOR.plain.dumps(HistoryData.serializer().list, data))
                        }
                    }
                }
            }
        }
    }

    fun exportAllSmallFilesByDate(dir: String, exportType: ExportType) = runBlocking(context = Dispatchers.IO){

        val dates = transaction{
            historydatas.run{
                slice(date).selectAll().distinct().map { it[date] }.toSet()
            }
        }

        dates.launchInAndJoinAll(this){ date ->
            val file = dir.trim('/') + "/${exportType.name.toLowerCase()}/$date.${exportType.name.toLowerCase()}"
            transaction {
                val data = HistoryData[date]

                with(File(file)) {
                    this.parentFile.mkdirs()
                    createNewFile()

                    when(exportType){
                        ExportType.CSV -> {
                            writeText(HistoryData.csvHeader)
                            appendText("\n")

                            data.forEach { t, dat ->
                                appendText(dat.toCSV())
                                appendText("\n")
                            }
                        }
                        ExportType.JSON -> {
                            writeText(JSON.plain.stringify((IntSerializer to HistoryData.serializer()).map, data))
                        }
                        ExportType.CBOR -> {
                            writeText(CBOR.plain.dumps((IntSerializer to HistoryData.serializer()).map, data))
                        }
                    }
                }
            }
        }
    }
}